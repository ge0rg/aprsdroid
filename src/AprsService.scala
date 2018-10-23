package org.aprsdroid.app

import _root_.android.app.Service
import _root_.android.content.{Context, Intent, IntentFilter}
import _root_.android.location._
import _root_.android.os.{Bundle, IBinder, Handler}
import _root_.android.preference.PreferenceManager
import _root_.android.util.Log
import _root_.android.widget.Toast

import _root_.net.ab0oo.aprs.parser._

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

	implicit def block2runnable(block: => Unit) =
		new Runnable() {
			def run() { block }
		}

}

class AprsService extends Service {
	import AprsService._
	val TAG = "APRSdroid.Service"

	lazy val APP_VERSION = "APDR%s".format(
		getPackageManager().getPackageInfo(getPackageName(), 0).versionName
			filter (_.isDigit) take 2)

	lazy val prefs = new PrefsWrapper(this)

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
		unregisterReceiver(msgNotifier)
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
		val status_spd = if (prefs.getBoolean("priv_spdbear", true))
			AprsPacket.formatCourseSpeed(location) else ""
		val status_freq = AprsPacket.formatFreq(status_spd, prefs.getStringFloat("frequency", 0.0f))
		val status_alt = if (prefs.getBoolean("priv_altitude", true))
			AprsPacket.formatAltitude(location) else ""
		newPacket(new PositionPacket(
			pos, status_spd + status_freq + status_alt + " " + status, /* messaging = */ true))
	}

	def sendPacket(packet : APRSPacket, status_postfix : String) {
		scala.concurrent.ops.spawn {
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
		postAddPost(StorageDatabase.Post.TYPE_INCMG, R.string.post_incmg, post)
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

