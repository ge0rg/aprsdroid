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
	// broadcast actions
	val UPDATE = PACKAGE + ".UPDATE"	// something added to the log
	val MESSAGE = PACKAGE + ".MESSAGE"	// we received a message/ack
	val MESSAGETX = PACKAGE + ".MESSAGETX"	// we created a message for TX
	// broadcast intent extras
	val LOCATION = PACKAGE + ".LOCATION"
	val STATUS = PACKAGE + ".STATUS"
	val PACKET = PACKAGE + ".PACKET"

	val FAST_LANE_ACT = 30000

	def intent(ctx : Context, action : String) : Intent = {
		new Intent(action, null, ctx, classOf[AprsService])
	}

	var running = false

	implicit def block2runnable(block: => Unit) =
		new Runnable() {
			def run() { block }
		}

}

class AprsService extends Service with LocationListener {
	import AprsService._
	val TAG = "APRSdroid.Service"

	lazy val prefs = new PrefsWrapper(this)

	lazy val locMan = getSystemService(Context.LOCATION_SERVICE).asInstanceOf[LocationManager]

	val handler = new Handler()

	lazy val db = StorageDatabase.open(this)

	lazy val msgService = new MessageService(this)
	lazy val msgNotifier = msgService.createMessageNotifier()

	var poster : AprsIsUploader = null

	var singleShot = false
	var lastLoc : Location = null
	var fastLaneLoc : Location = null

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

	def requestLocations(stay_on : Boolean) {
		// get update interval and distance
		val upd_int = prefs.getStringInt("interval", 10)
		val upd_dist = prefs.getStringInt("distance", 10)
		val gps_act = prefs.getString("gps_activation", "med")
		if (stay_on || (gps_act == "always")) {
			locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER,
				0, 0, this)
		} else {
			// for GPS precision == medium, we use getGpsInterval()
			locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER,
				upd_int * 60000 - getGpsInterval(), upd_dist * 1000, this)
		}
		if (prefs.getBoolean("netloc", false)) {
			locMan.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
				upd_int * 60000, upd_dist * 1000, this)
		}
	}

	def handleStart(i : Intent) {
		// get update interval and distance
		val upd_int = prefs.getStringInt("interval", 10)
		val upd_dist = prefs.getStringInt("distance", 10)

		// display notification (even though we are not actually started yet,
		// but we need this to prevent error message reordering)
		fastLaneLoc = null
		if (i.getAction() == SERVICE_ONCE) {
			// if already running, we want to send immediately and continue;
			// otherwise, we finish after a single position report
			lastLoc = null
			// set to true if not yet running or already running singleShot
			singleShot = !running || singleShot
			if (singleShot)
					showToast(getString(R.string.service_once))
		} else
			showToast(getString(R.string.service_start).format(upd_int, upd_dist))

		// the poster needs to be running before location updates come in
		if (!running) {
			running = true
			startPoster()

			// register for outgoing message notifications
			registerReceiver(msgNotifier, new IntentFilter(AprsService.MESSAGETX))
		}

		// continuous GPS tracking for single shot mode
		requestLocations(singleShot)

		val callssid = prefs.getCallSsid()
		val message = "%s: %d min, %d km".format(callssid, upd_int, upd_dist)
		ServiceNotifier.instance.start(this, message)
	}

	def startPoster() {
		if (poster != null)
			poster.stop()
		poster = AprsIsUploader.instanciateUploader(this, prefs)
		poster.start()
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
		locMan.removeUpdates(this);
		unregisterReceiver(msgNotifier)
		ServiceNotifier.instance.stop(this)
	}

	def getGpsInterval() : Int = {
		val gps_act = prefs.getString("gps_activation", "med")
		if (gps_act == "med") FAST_LANE_ACT
				else  0
	}

	def startFastLane() {
		Log.d(TAG, "switching to fast lane");
		// request fast update rate
		locMan.removeUpdates(this);
		requestLocations(true)
		handler.postDelayed({ stopFastLane(true) }, FAST_LANE_ACT)
	}

	def stopFastLane(post : Boolean) {
		if (!running)
			return;
		Log.d(TAG, "switching to slow lane");
		if (post && fastLaneLoc != null) {
			Log.d(TAG, "stopFastLane: posting " + fastLaneLoc);
			postLocation(fastLaneLoc)
		}
		fastLaneLoc = null
		// reset update speed
		locMan.removeUpdates(this);
		requestLocations(false)
	}

	def goingFastLane(location : Location) : Boolean = {
		if (fastLaneLoc == null) {
			// need to set fastLaneLoc before re-requesting locations
			fastLaneLoc = location
			startFastLane()
		} else
			fastLaneLoc = location
		return true
	}

	def smartBeaconSpeedRate(speed : Float) : Int = {
		val SB_FAST_SPEED = 28 // [m/s] = ~100km/h
		val SB_FAST_RATE = 60
		val SB_SLOW_SPEED = 1 // [m/s] = 3.6km/h
		val SB_SLOW_RATE = 1200
		if (speed <= SB_SLOW_SPEED)
			SB_SLOW_RATE
		else if (speed >= SB_FAST_SPEED)
			SB_FAST_RATE
		else
			((SB_SLOW_RATE - SB_FAST_RATE) * (SB_FAST_SPEED - speed) / (SB_FAST_SPEED-SB_SLOW_SPEED)).toInt
	}

	// returns the angle between two bearings
	def getBearingAngle(alpha : Float, beta : Float) : Float = {
		val delta = math.abs(alpha-beta)%360
		if (delta <= 180) delta else (360-delta)
	}
	// obtain max speed in [m/s] from moved distance, last and current location
	def getSpeed(location : Location) : Float = {
		val dist = location.distanceTo(lastLoc)
		val t_diff = location.getTime - lastLoc.getTime
		math.max(math.max(dist*1000/t_diff, location.getSpeed), lastLoc.getSpeed)
	}

	def smartBeaconCornerPeg(location : Location) : Boolean = {
		if (!location.hasBearing || !lastLoc.hasBearing)
			return false
		val SB_TURN_TIME = 30
		val SB_TURN_MIN = 10
		val SB_TURN_SLOPE = 240.0
		val t_diff = location.getTime - lastLoc.getTime
		val turn = getBearingAngle(location.getBearing, lastLoc.getBearing)
		// threshold depends on slope/speed [mph]
		val threshold = SB_TURN_MIN + SB_TURN_SLOPE/(getSpeed(location)*2.23693629)

		Log.d(TAG, "smartBeaconCornerPeg: %1.0f < %1.0f %d/%d".format(turn, threshold,
			t_diff/1000, SB_TURN_TIME))
		// need to corner peg if turn time reached and turn > threshold
		(t_diff/1000 >= SB_TURN_TIME && turn > threshold)
	}

	// return true if current position is "new enough" vs. lastLoc
	def smartBeaconCheck(location : Location) : Boolean = {
		if (lastLoc == null)
			return true
		if (smartBeaconCornerPeg(location))
			return true
		val dist = location.distanceTo(lastLoc)
		val t_diff = location.getTime - lastLoc.getTime
		val speed = getSpeed(location)
		//if (location.hasSpeed && location.hasBearing)
		val speed_rate = smartBeaconSpeedRate(speed)
		Log.d(TAG, "smartBeaconCheck: %1.0fm, %1.2fm/s -> %d/%ds - %s".format(dist, speed,
			t_diff/1000, speed_rate, (t_diff/1000 >= speed_rate).toString))
		if (t_diff/1000 >= speed_rate)
			true
		else
			false
	}

	// LocationListener interface
	override def onLocationChanged(location : Location) {
		val upd_int = prefs.getStringInt("interval", 10) * 60000
		val upd_dist = prefs.getStringInt("distance", 10) * 1000
		//Log.d(TAG, "onLocationChanged: n=" + location)
		//Log.d(TAG, "onLocationChanged: l=" + lastLoc)
		if (/* smart beaconing == */ true) {
			if (!smartBeaconCheck(location))
				return
		} else
		if (lastLoc != null &&
		    (location.getTime - lastLoc.getTime < (upd_int  - getGpsInterval()) ||
		     location.distanceTo(lastLoc) < upd_dist)) {
			//Log.d(TAG, "onLocationChanged: ignoring premature location")
			return
		}
		// check if we need to go fast lane
		val gps_act = prefs.getString("gps_activation", "med")
		if (gps_act == "med" && location.getProvider == LocationManager.GPS_PROVIDER) {
			if (goingFastLane(location))
				return
		}
		postLocation(location)
	}

	def appVersion() : String = {
		val pi = getPackageManager().getPackageInfo(getPackageName(), 0)
		"APDR%s".format(pi.versionName filter (_.isDigit) take 2)
	}

	def postLocation(location : Location) {
		lastLoc = location

		val i = new Intent(UPDATE)
		i.putExtra(LOCATION, location)

		val callssid = prefs.getCallSsid()
		var symbol = prefs.getString("symbol", "")
		if (symbol.length != 2)
			symbol = getString(R.string.default_symbol)
		val status = prefs.getString("status", getString(R.string.default_status))
		val packet = AprsPacket.formatLoc(callssid, appVersion(), symbol, status, location)

		Log.d(TAG, "packet: " + packet)
		val result = try {
			val status = poster.update(packet)
			i.putExtra(STATUS, status)
			i.putExtra(PACKET, packet.toString)
			val prec_status = "%s (±%dm)".format(status, location.getAccuracy.asInstanceOf[Int])
			addPost(StorageDatabase.Post.TYPE_POST, prec_status, packet.toString)
			prec_status
		} catch {
			case e : Exception =>
				i.putExtra(PACKET, e.getMessage())
				addPost(StorageDatabase.Post.TYPE_ERROR, "Error", e.getMessage())
				e.printStackTrace()
				e.getMessage()
		}
		if (singleShot) {
			singleShot = false
			stopSelf()
		} else {
			val message = "%s: %s".format(callssid, result)
			ServiceNotifier.instance.start(this, message)
		}
	}

	override def onProviderDisabled(provider : String) {
		Log.d(TAG, "onProviderDisabled: " + provider)
		val netloc_available = locMan.getProviders(true).contains(LocationManager.NETWORK_PROVIDER)
		val netloc_usable = netloc_available && prefs.getBoolean("netloc", false)
		if (provider == LocationManager.GPS_PROVIDER &&
			netloc_usable == false) {
			// GPS was our last data source, we have to complain!
			Toast.makeText(this, R.string.service_no_location, Toast.LENGTH_LONG).show()
		}
	}
	override def onProviderEnabled(provider : String) {
		Log.d(TAG, "onProviderEnabled: " + provider)
	}
	override def onStatusChanged(provider : String, st: Int, extras : Bundle) {
		Log.d(TAG, "onStatusChanged: " + provider)
	}

	def parsePacket(ts : Long, message : String) {
		try {
			val fap = new Parser().parse(message)
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
			parsePacket(ts, message)
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

}

