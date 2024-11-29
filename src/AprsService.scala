package org.aprsdroid.app

import _root_.android.app.Service
import _root_.android.content.{Context, Intent, IntentFilter}
import _root_.android.location._
import _root_.android.os.{Bundle, IBinder, Handler}
import _root_.android.preference.PreferenceManager
import _root_.android.util.Log
import _root_.android.widget.Toast

import _root_.net.ab0oo.aprs.parser._
import scala.collection.mutable
import java.time.Instant

object AprsService {
	val PACKAGE = "org.aprsdroid.app"
	// action intents
	val SERVICE = PACKAGE + ".SERVICE"
	val SERVICE_ONCE = PACKAGE + ".ONCE"
	val SERVICE_SEND_PACKET = PACKAGE + ".SEND_PACKET"
	val SERVICE_FREQUENCY = PACKAGE + ".FREQUENCY"
	val SERVICE_STOP = PACKAGE + ".SERVICE_STOP"
	// event intents
	val SERVICE_STARTED = PACKAGE + ".SERVICE_STARTED"
	val SERVICE_STOPPED = PACKAGE + ".SERVICE_STOPPED"
	val POSITION = PACKAGE + ".POSITION"
	val MICLEVEL = PACKAGE + ".MICLEVEL" // internal volume event intent
	val LINK_ON = PACKAGE + ".LINK_ON"
	val LINK_OFF = PACKAGE + ".LINK_OFF"
	val LINK_INFO = PACKAGE + ".LINK_INFO"
	// broadcast actions
	val UPDATE = PACKAGE + ".UPDATE"	// something added to the log
	val MESSAGE = PACKAGE + ".MESSAGE"	// we received a message/ack
	val MESSAGETX = PACKAGE + ".MESSAGETX"	// we created a message for TX
	// broadcast intent extras
	// SERVICE_STARTED
	val API_VERSION = "api_version"		// API version
	val CALLSIGN = "callsign"		// callsign + ssid of the user
	// UPDATE
	val TYPE = "type"			// type
	val STATUS = "status"			// content
	// POSITION
	val LOCATION = "location"		// Location object
	val SOURCE = "source"			// sender callsign
	val PACKET = "packet"			// raw packet content
	// MESSAGE
	//  +- SOURCE
	val DEST = "dest"			// destination callsign
	val BODY = "body"			// body of the message

	// APRSdroid API version
	val API_VERSION_CODE = 1

	// private intents for message handling
	lazy val MSG_PRIV_INTENT = new Intent(MESSAGE).setPackage("org.aprsdroid.app")
	lazy val MSG_TX_PRIV_INTENT = new Intent(MESSAGETX).setPackage("org.aprsdroid.app")

	def intent(ctx : Context, action : String) : Intent = {
		new Intent(action, null, ctx, classOf[AprsService])
	}

	var running = false
	var link_error = 0

	implicit def block2runnable[F](f: => F) = new Runnable() { def run() { f } }
}

class AprsService extends Service {
	import AprsService._
	val TAG = "APRSdroid.Service"

	lazy val APP_VERSION = "APDR%s".format(
		getPackageManager().getPackageInfo(getPackageName(), 0).versionName
			filter (_.isDigit) take 2)

	lazy val prefs = new PrefsWrapper(this)

	lazy val dedupeTime = prefs.getStringInt("p.dedupe", 30) // Fetch NUM_OF_RETRIES from prefs, defaulting to 7 if not found

	lazy val digipeaterpath = prefs.getString("digipeater_path", "WIDE1,WIDE2")

	val handler = new Handler()

	lazy val db = StorageDatabase.open(this)

	lazy val msgService = new MessageService(this)
	lazy val locSource = LocationSource.instanciateLocation(this, prefs)
	lazy val msgNotifier = msgService.createMessageNotifier()

	var poster : AprsBackend = null

	var singleShot = false

	override def onStart(i : Intent, startId : Int) {
		Log.d(TAG, "onStart: " + i + ", " + startId);
		super.onStart(i, startId)
		handleStart(i)
	}

	override def onStartCommand(i : Intent, flags : Int, startId : Int) : Int = {
		Log.d(TAG, "onStartCommand: " + i + ", " + flags + ", " + startId);
		handleStart(i)
		Service.START_REDELIVER_INTENT
	}

	def handleStart(i : Intent) {
		if (i.getAction() == SERVICE_STOP) {
                        // explicitly disabled, remember this
                        prefs.setBoolean("service_running", false)
			if (running)
				stopSelf()
			return
		} else
		if (i.getAction() == SERVICE_SEND_PACKET) {
			if (!running) {
				Log.d(TAG, "SEND_PACKET ignored, service not running.")
				return
			}
			val data_field = i.getStringExtra("data")
			if (data_field == null) {
				Log.d(TAG, "SEND_PACKET ignored, data extra is empty.")
				return
			}
			val p = Parser.parseBody(prefs.getCallSsid(), APP_VERSION, null,
				data_field)
			sendPacket(p)
			return
		} else
		if (i.getAction() == SERVICE_FREQUENCY) {
			val data_field = i.getStringExtra("frequency")
			if (data_field == null) {
				Log.d(TAG, "FREQUENCY ignored, 'frequency' extra is empty.")
				return
			}
                        val freq_cleaned = data_field.replace("MHz", "").trim
                        val freq = try { freq_cleaned.toFloat; freq_cleaned } catch { case _ : Throwable => "" }
                        if (prefs.getString("frequency", null) != freq) {
                                prefs.set("frequency", freq)
                                if (!running) return
                                // XXX: fall through into SERVICE_ONCE
			} else return
		}


		// display notification (even though we are not actually started yet,
		// but we need this to prevent error message reordering)
		val toastString = if (i.getAction() == SERVICE_ONCE) {
			// if already running, we want to send immediately and continue;
			// otherwise, we finish after a single position report
			// set to true if not yet running or already running singleShot
			singleShot = !running || singleShot
			if (singleShot)
					getString(R.string.service_once)
			else null
		} else {
			getString(R.string.service_start)
		}
		// only show toast on newly started service
		if (toastString != null)
			showToast(toastString.format(
				prefs.getLocationSourceName(),
				prefs.getBackendName()))

		val callssid = prefs.getCallSsid()
		ServiceNotifier.instance.start(this, callssid)

		// the poster needs to be running before location updates come in
		if (!running) {
			running = true
			startPoster()

			// register for outgoing message notifications
			registerReceiver(msgNotifier, new IntentFilter(AprsService.MESSAGETX))
		} else
			onPosterStarted()
	}

	def startPoster() {
		if (poster != null)
			poster.stop()
		poster = AprsBackend.instanciateUploader(this, prefs)
		if (poster.start())
			onPosterStarted()
	}

	def onPosterStarted() {
		Log.d(TAG, "onPosterStarted")
		// (re)start location source, get location source name
		val loc_info = locSource.start(singleShot)

		val callssid = prefs.getCallSsid()
		val message = "%s: %s".format(callssid, loc_info)
		ServiceNotifier.instance.start(this, message)

		msgService.sendPendingMessages()

		sendBroadcast(new Intent(SERVICE_STARTED)
			.putExtra(API_VERSION, API_VERSION_CODE)
			.putExtra(CALLSIGN, callssid))

		// startup completed, remember state
		if (!singleShot)
			prefs.setBoolean("service_running", true)
	}

	override def onBind(i : Intent) : IBinder = null
		
	override def onUnbind(i : Intent) : Boolean = false
		
	def showToast(msg : String) {
		Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
		addPost(StorageDatabase.Post.TYPE_INFO, null, msg)
	}

	override def onDestroy() {
		running = false
		link_error = 0
		// catch FC when service is killed from outside
		if (poster != null) {
			poster.stop()
			showToast(getString(R.string.service_stop))

			sendBroadcast(new Intent(SERVICE_STOPPED))
		}
		msgService.stop()
		locSource.stop()
		scala.util.control.Exception.ignoring(classOf[IllegalArgumentException]) {
			unregisterReceiver(msgNotifier)
		}
		ServiceNotifier.instance.stop(this)
	}

	def newPacket(payload : InformationField) = {
		val digipath = prefs.getString("digi_path", "WIDE1-1")
		new APRSPacket(prefs.getCallSsid(), APP_VERSION,
			Digipeater.parseList(digipath, true), payload)
	}

	def formatLoc(symbol : String, status : String, location : Location) = {
		val pos = new Position(location.getLatitude, location.getLongitude, 0,
				     symbol(0), symbol(1))
		pos.setPositionAmbiguity(prefs.getStringInt("priv_ambiguity", 0))
		val status_spd = if (prefs.getBoolean("priv_spdbear", true)) {
			if(prefs.getBoolean("compressed_location", false)) {
				// Compressed format
				AprsPacket.formatCourseSpeedCompressed(location)
			} else {
				AprsPacket.formatCourseSpeed(location)
			}
		} else ""
		val status_freq = AprsPacket.formatFreq(status_spd, prefs.getStringFloat("frequency", 0.0f))
		val status_alt = if (prefs.getBoolean("priv_altitude", true)) {
			// if speed is empty then use compressed altitude, otherwise use full length altitude
			if(prefs.getBoolean("compressed_location", false) && status_spd == "") {
				// Compressed format
				AprsPacket.formatAltitudeCompressed(location)
			} else {
				AprsPacket.formatAltitude(location)
			}
		} else ""
		if(prefs.getBoolean("compressed_location", false)) {
			if(status_spd == "") {
				// Speed is empty, so we can use a compressed altitude
				if(status_alt == "") {
					// Altitude is empty, so don't send any altitude data
					pos.setCsTField(" sT")
				} else {
					// 3 signifies current GPS fix, GGA altitude, software compressed.
					pos.setCsTField(status_alt + "3")
				}
				val packet = new PositionPacket(
					pos, status_freq + " " + status, /* messaging = */ true)
				packet.setCompressedFormat(true)
				newPacket(packet)
			} else {
				// Speed is present, so we need to append the altitude to the end of the packet using the
				// uncompressed method
				// Apply the csT field with speed and course
				// [ signifies current GPS fix, RMC speed, software compressed.
				pos.setCsTField(status_spd + "[")
				val packet = new PositionPacket(
					pos, status_freq + status_alt + " " + status, /* messaging = */ true)
				packet.setCompressedFormat(true)
				newPacket(packet)
			}
		} else {
			val packet = new PositionPacket(
				pos, status_spd + status_freq + status_alt + " " + status, /* messaging = */ true)
			newPacket(packet)
		}
		//val comment = status_spd + status_freq + status_alt + " " + status;
		// TODO: slice after 43 bytes, not after 43 UTF-8 codepoints
		//newPacket(new PositionPacket(pos, comment.slice(0, 43), /* messaging = */ true))
	}

	def sendPacket(packet : APRSPacket, status_postfix : String) {
                implicit val ec = scala.concurrent.ExecutionContext.global
		scala.concurrent.Future {
		val status = try {
			val status = poster.update(packet)
			val full_status = status + status_postfix
			addPost(StorageDatabase.Post.TYPE_POST, full_status, packet.toString)
			full_status
		} catch {
			case e : Exception =>
				addPost(StorageDatabase.Post.TYPE_ERROR, "Error", e.toString())
				e.printStackTrace()
				e.toString()
		}
		handler.post { sendPacketFinished(status) }
		}
	}
	def sendPacket(packet : APRSPacket) { sendPacket(packet, "") }

	def postLocation(location : Location) {
		var symbol = prefs.getString("symbol", "")
		if (symbol.length != 2)
			symbol = getString(R.string.default_symbol)
		val status = prefs.getString("status", getString(R.string.default_status))
		val packet = formatLoc(symbol, status, location)

		Log.d(TAG, "packet: " + packet)
		sendPacket(packet, " (±%dm)".format(location.getAccuracy.asInstanceOf[Int]))
	}

	def sendPacketFinished(result : String) {
		if (singleShot) {
			singleShot = false
			stopSelf()
		} else {
			val message = "%s: %s".format(prefs.getCallSsid(), result)
			ServiceNotifier.instance.notifyPosition(this, prefs, message)
		}
	}

	def sendTestPacket(packetString: String): Unit = {
		// Parse the incoming string to an APRSPacket object
		try {
			val testPacket = Parser.parse(packetString)

			// Define additional information to be passed as status postfix
			val digistatus = " - Digipeated"

			// Send the packet with the additional status postfix
			sendPacket(testPacket, digistatus)

			Log.d("APRSdroid.Service", s"Successfully sent packet: $packetString")
		} catch {
			case e: Exception =>
				Log.e("APRSdroid.Service", s"Failed to send packet: $packetString", e)
		}
	}

	def parsePacket(ts : Long, message : String, source : Int) {
		try {
			var fap = Parser.parse(message)
			if (fap.getType() == APRSTypes.T_THIRDPARTY) {
				Log.d(TAG, "parsePacket: third-party packet from " + fap.getSourceCall())
				val inner = fap.getAprsInformation().toString()
				// strip away leading "}"
				fap = Parser.parse(inner.substring(1, inner.length()))
			}

			val callssid = prefs.getCallSsid()
			if (source == StorageDatabase.Post.TYPE_INCMG &&
			    fap.getSourceCall().equalsIgnoreCase(callssid) &&
			    fap.getLastUsedDigi() != null) {
				Log.i(TAG, "got digipeated own packet")
				val message = getString(R.string.got_digipeated, fap.getLastUsedDigi(),
					fap.getAprsInformation().toString())
				ServiceNotifier.instance.notifyPosition(this, prefs, message, "dgp_")
				return
			}

			if (fap.getAprsInformation() == null) {
				Log.d(TAG, "parsePacket() misses payload: " + message)
				return
			}
			if (fap.hasFault())
				throw new Exception("FAP fault")
			fap.getAprsInformation() match {
				case pp : PositionPacket => addPosition(ts, fap, pp, pp.getPosition(), null)
				case op : ObjectPacket => addPosition(ts, fap, op, op.getPosition(), op.getObjectName())
				case msg : MessagePacket => msgService.handleMessage(ts, fap, msg)
			}
		} catch {
		case e : Exception =>
			Log.d(TAG, "parsePacket() unsupported packet: " + message)
			e.printStackTrace()
		}
	}

	def getCSE(field : InformationField) : CourseAndSpeedExtension = {
		field.getExtension() match {
			case cse : CourseAndSpeedExtension => cse
			case _ => null
		}
	}
	def addPosition(ts : Long, ap : APRSPacket, field : InformationField, pos : Position, objectname : String) {
		val cse = getCSE(field)
		db.addPosition(ts, ap, pos, cse, objectname)

		sendBroadcast(new Intent(POSITION)
			.putExtra(SOURCE, ap.getSourceCall())
			.putExtra(LOCATION, AprsPacket.position2location(ts, pos, cse))
			.putExtra(CALLSIGN, if (objectname != null) objectname else ap.getSourceCall())
			.putExtra(PACKET, ap.toString())
		)
	}

	def addPost(t : Int, status : String, message : String) {
		val ts = System.currentTimeMillis()
		db.addPost(ts, t, status, message)
		if (t == StorageDatabase.Post.TYPE_POST || t == StorageDatabase.Post.TYPE_INCMG) {
			parsePacket(ts, message, t)
		} else {
			// only log status messages
			Log.d(TAG, "addPost: " + status + " - " + message)
		}
		sendBroadcast(new Intent(UPDATE)
			.putExtra(TYPE, t)
			.putExtra(STATUS, message))
	}
	// support for translated IDs
	def addPost(t : Int, status_id : Int, message : String) {
		addPost(t, getString(status_id), message)
	}

	def postAddPost(t : Int, status_id : Int, message : String) {
		// only log "info" if enabled in prefs
		if (t == StorageDatabase.Post.TYPE_INFO && prefs.getBoolean("conn_log", false) == false)
			return
		handler.post {
			addPost(t, status_id, message)
			if (t == StorageDatabase.Post.TYPE_INCMG)
				msgService.sendPendingMessages()
			else if (t == StorageDatabase.Post.TYPE_ERROR)
				stopSelf()
		}
	}
	def postSubmit(post : String) {
		// Log the incoming post message for debugging
		Log.d("APRSdroid.Service", s"Incoming post: $post")	
		postAddPost(StorageDatabase.Post.TYPE_INCMG, R.string.post_incmg, post)
		
		// Process the incoming post
		processIncomingPost(post)		
	}

	// Map to store recent digipeats with their timestamps
	val recentDigipeats: mutable.Map[String, Instant] = mutable.Map()

	// Function to add or update the digipeat
	def storeDigipeat(sourceCall: String, destinationCall: String, payload: String): Unit = {
	  // Unique identifier using source call, destination call, and payload
	  val key = s"$sourceCall>$destinationCall:$payload"
	  recentDigipeats(key) = Instant.now() // Store the current timestamp
	}

	// Function to filter digipeats that are older than dedupeTime seconds
	def isDigipeatRecent(sourceCall: String, destinationCall: String, payload: String): Boolean = {
	  // Unique identifier using source call, destination call, and payload
	  val key = s"$sourceCall>$destinationCall:$payload"
	  recentDigipeats.get(key) match {
		case Some(timestamp) =>
		  // Check if the packet was heard within the last 30 seconds
		  Instant.now().isBefore(timestamp.plusSeconds(dedupeTime))
		case None =>
		  false // Not found in recent digipeats
	  }
	}

	// Function to clean up old entries
	def cleanupOldDigipeats(): Unit = {
	  val now = Instant.now()
	  // Retain only those digipeats that are within the last 30 seconds
	  recentDigipeats.retain { case (_, timestamp) =>
		now.isBefore(timestamp.plusSeconds(dedupeTime))
	  }
	}

	def processIncomingPost(post: String) {
	    Log.d(TAG, "POST STRING TEST: " + post)  // Log the incoming post for debugging

		// Check if backendName contains "KISS" or "AFSK"
		if (prefs.getBackendName().contains("KISS") || prefs.getBackendName().contains("AFSK")) {
			android.util.Log.d("PrefsAct", "Backend contains KISS or AFSK")
		} else {
			android.util.Log.d("PrefsAct", "Backend does not contain KISS or AFSK")
			return
		}	
		//TODO, Add workaround for unsupported formats.
		// Attempt to parse the incoming post to an APRSPacket.
		val packet = try {
			Parser.parse(post) // Attempt to parse
		} catch {
			case e: Exception =>
				Log.e("Parsing FAILED!", s"Failed to parse packet: $post", e)
				return // Exit the function if parsing fails
		}

		// Check if both digipeating and regeneration are enabled. Temp fix until re-implementation. Remove later on.
		if (prefs.isDigipeaterEnabled() && prefs.isRegenerateEnabled()) {
			Log.d("APRSdroid.Service", "Both Digipeating and Regeneration are enabled; Set Regen to false.")
			prefs.setBoolean("p.regenerate", false) // Disable regeneration
			
		}	

		// New regen
		if (!prefs.isDigipeaterEnabled() && prefs.isRegenerateEnabled()) {
			Log.d("APRSdroid.Service", "Regen enabled")
			sendTestPacket(packet.toString)
			return // Exit if both digipeating and regeneration are enabled
		}	
			
		// Check if the digipeating setting is enabled
		if (!prefs.isDigipeaterEnabled()) {
				Log.d("APRSdroid.Service", "Digipeating is disabled; skipping processing.")
				return // Exit if digipeating is not enabled
		}	

		cleanupOldDigipeats() // Clean up old digipeats before processing

		// Try to parse the incoming post to an APRSPacket
		try {
			// Now you can access the source call from the packet
			val callssid = prefs.getCallSsid()			
			val sourceCall = packet.getSourceCall()
			val destinationCall = packet.getDestinationCall();
			val lastUsedDigi = packet.getDigiString()
			val payload = packet.getAprsInformation()	

			val payloadString = packet.getAprsInformation().toString() // Ensure payload is a String


			// Check if callssid matches sourceCall; if they match, do not digipeat
			if (callssid == sourceCall) {
				Log.d("APRSdroid.Service", s"No digipeat: callssid ($callssid) matches source call ($sourceCall).")
				return // Exit if no digipeating is needed
			}				
			
			// Check if this packet has been digipeated recently
			if (isDigipeatRecent(sourceCall, destinationCall, payloadString)) {
			Log.d("APRSdroid.Service", s"Packet from $sourceCall to $destinationCall and $payload has been heard recently, skipping digipeating.")
			  return // Skip processing this packet
			}			
						
			
			val (modifiedDigiPath, digipeatOccurred) = processDigiPath(lastUsedDigi, callssid)			


			Log.d("APRSdroid.Service", s"Source: $sourceCall")
			Log.d("APRSdroid.Service", s"Destination: $destinationCall")
			Log.d("APRSdroid.Service", s"Digi: $lastUsedDigi")
			Log.d("APRSdroid.Service", s"Modified Digi Path: $modifiedDigiPath")
			
			Log.d("APRSdroid.Service", s"Payload: $payload")

			// Format the string for sending
			val testPacket = s"$sourceCall>$destinationCall,$modifiedDigiPath:$payload"

			// Optionally, send a test packet with the formatted string only if a digipeat occurred
			if (digipeatOccurred) {
				sendTestPacket(testPacket)
								
				// Store the digipeat to the recent list
				storeDigipeat(sourceCall, destinationCall, payloadString)				
				
			} else {
				Log.d("APRSdroid.Service", "No digipeat occurred, not sending a test packet.")
			}		
			
		} catch {
			case e: Exception =>
				Log.e("APRSdroid.Service", s"Failed to parse packet: $post", e)
		}
	}

	def processDigiPath(lastUsedDigi: String, callssid: String): (String, Boolean) = {
		// Log the input Digi path
		Log.d("APRSdroid.Service", s"Original Digi Path: '$lastUsedDigi'")

		// If lastUsedDigi is empty, return it unchanged
		if (lastUsedDigi.trim.isEmpty) {
			Log.d("APRSdroid.Service", "LastUsedDigi is empty, returning unchanged.")
			return (lastUsedDigi, false)
		}

		// Remove leading comma for easier processing
		val trimmedPath = lastUsedDigi.stripPrefix(",")

		// Split the path into components, avoiding empty strings
		val pathComponents = trimmedPath.split(",").toList.filter(_.nonEmpty)
		val digipeaterPaths = digipeaterpath.split(",").toList.filter(_.nonEmpty)

		// Create a new list of components with modifications
		val (modifiedPath, modified) = pathComponents.foldLeft((List.empty[String], false)) {
			case ((acc, hasModified), component) =>
			
				// Check if callssid* is in the path and skip if found
				if (component == s"$callssid*") {
					// Skip digipeating if callssid* is found
					return (lastUsedDigi, false) // Return the original path, do not modify
									
				} else if (!hasModified && (digipeaterPaths.exists(path => component.split("-")(0) == path) || digipeaterPaths.contains(component) || component == callssid)) {
					// We need to check if the first unused component matches digipeaterpath
					if (acc.isEmpty || acc.last.endsWith("*")) {
						// This is the first unused component
						component match {
	
						  case w if w.matches(".*-(\\d+)$") =>
							// Extract the number from the suffix
							val number = w.split("-").last.toInt
							// Decrement the number
							val newNumber = number - 1 
														
							if (newNumber == 0 || w == callssid) {
							  // If the number is decremented to 0, remove the component and insert callssid*
							  (acc :+ s"$callssid*", true)
							  
							} else {
							  // Otherwise, decrement the number and keep the component
							  val newComponent = w.stripSuffix(s"-$number") + s"-$newNumber"
							  (acc :+ s"$callssid*" :+ newComponent, true)
							}

						  case _ =>
							// Leave unchanged if there's no -N suffix
							(acc :+ component, hasModified)
						}

					} else {
						// If the first unused component doesn't match digipeaterpath, keep unchanged
						(acc :+ component, hasModified)
					}
				
				} else {
					// Keep the component as it is
					(acc :+ component, hasModified)
				}
		}

		// Rebuild the modified path
		val resultPath = modifiedPath.mkString(",")

		// Log the modified path before returning
		Log.d("APRSdroid.Service", s"Modified Digi Path: '$resultPath'")

		// If no modification occurred, return the original lastUsedDigi
		if (resultPath == trimmedPath) {
			Log.d("APRSdroid.Service", "No modifications were made; returning the original path.")
			return (lastUsedDigi, false)
		}

		// Return the modified path with a leading comma
		(s"$resultPath", true)
	}

	def postAbort(post : String) {
		postAddPost(StorageDatabase.Post.TYPE_ERROR, R.string.post_error, post)
	}
	def postPosterStarted() {
		handler.post {
			onPosterStarted()
		}
	}

	def postLinkOn(link : Int) {
                link_error = 0
		sendBroadcast(new Intent(LINK_ON).putExtra(LINK_INFO, link))
		val message = getString(R.string.status_linkon, getString(link))
		ServiceNotifier.instance.start(this, message)
	}

	def postLinkOff(link : Int) {
                link_error = link
		sendBroadcast(new Intent(LINK_OFF).putExtra(LINK_INFO, link))
		val message = getString(R.string.status_linkoff, getString(link))
		ServiceNotifier.instance.start(this, message)
	}
}

