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
	// intent actions
	val SERVICE = PACKAGE + ".SERVICE"
	val SERVICE_ONCE = PACKAGE + ".ONCE"
	val SERVICE_SEND_PACKET = PACKAGE + ".SEND_PACKET"
	// broadcast actions
	val UPDATE = PACKAGE + ".UPDATE"	// something added to the log
	val MESSAGE = PACKAGE + ".MESSAGE"	// we received a message/ack
	val MESSAGETX = PACKAGE + ".MESSAGETX"	// we created a message for TX
	// broadcast intent extras
	val LOCATION = PACKAGE + ".LOCATION"
	val STATUS = PACKAGE + ".STATUS"
	val PACKET = PACKAGE + ".PACKET"
	val MICLEVEL = PACKAGE + ".MICLEVEL"

	def intent(ctx : Context, action : String) : Intent = {
		new Intent(action, null, ctx, classOf[AprsService])
	}

	var running = false

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
				prefs.getListItemName("loc_source", LocationSource.DEFAULT_CONNTYPE,
					R.array.p_locsource_ev, R.array.p_locsource_e),
				prefs.getListItemName("backend", AprsBackend.DEFAULT_CONNTYPE,
					R.array.p_conntype_ev, R.array.p_conntype_e)))

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
	}

	override def onBind(i : Intent) : IBinder = null
		
	override def onUnbind(i : Intent) : Boolean = false
		
	def showToast(msg : String) {
		Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
		addPost(StorageDatabase.Post.TYPE_INFO, null, msg)
	}

	override def onDestroy() {
		running = false
		// catch FC when service is killed from outside
		if (poster != null) {
			poster.stop()
			showToast(getString(R.string.service_stop))
		}
		msgService.stop()
		locSource.stop()
		unregisterReceiver(msgNotifier)
		ServiceNotifier.instance.stop(this)
	}

	def newPacket(payload : InformationField) = {
		// TODO: obtain digi path from prefs
		new APRSPacket(prefs.getCallSsid(), APP_VERSION, null, payload)
	}

	def formatLoc(symbol : String, status : String, location : Location) = {
		val pos = new Position(location.getLatitude, location.getLongitude, 0,
				     symbol(0), symbol(1))
		pos.setPositionAmbiguity(prefs.getStringInt("priv_ambiguity", 0))
		val status_spd = if (prefs.getBoolean("priv_spdbear", true))
			AprsPacket.formatCourseSpeed(location) else ""
		val status_alt = if (prefs.getBoolean("priv_altitude", true))
			AprsPacket.formatAltitude(location) else ""
		newPacket(new PositionPacket(
			pos, status_spd + status_alt + " " + status, /* messaging = */ true))
	}

	def sendPacket(packet : APRSPacket, status_postfix : String) = {
		try {
			val status = poster.update(packet)
			val full_status = status + status_postfix
			addPost(StorageDatabase.Post.TYPE_POST, full_status, packet.toString)
			full_status
		} catch {
			case e : Exception =>
				addPost(StorageDatabase.Post.TYPE_ERROR, "Error", e.getMessage())
				e.printStackTrace()
				e.getMessage()
		}
	}
	def sendPacket(packet : APRSPacket) : String = sendPacket(packet, "")

	def postLocation(location : Location) {
		val callssid = prefs.getCallSsid()
		var symbol = prefs.getString("symbol", "")
		if (symbol.length != 2)
			symbol = getString(R.string.default_symbol)
		val status = prefs.getString("status", getString(R.string.default_status))
		val packet = formatLoc(symbol, status, location)

		Log.d(TAG, "packet: " + packet)
		val result = sendPacket(packet, " (±%dm)".format(location.getAccuracy.asInstanceOf[Int]))
		if (singleShot) {
			singleShot = false
			stopSelf()
		} else {
			val message = "%s: %s".format(callssid, result)
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
			    fap.getSourceCall().equalsIgnoreCase(callssid)) {
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
				case pp : PositionPacket => db.addPosition(ts, fap, pp.getPosition(), null)
				case op : ObjectPacket => db.addPosition(ts, fap, op.getPosition(), op.getObjectName())
				case msg : MessagePacket => msgService.handleMessage(ts, fap, msg)
			}
		} catch {
		case e : Exception =>
			Log.d(TAG, "parsePacket() unsupported packet: " + message)
			e.printStackTrace()
		}
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
		sendBroadcast(new Intent(UPDATE).putExtra(STATUS, message))
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

}

