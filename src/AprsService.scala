package de.duenndns.aprsdroid

import _root_.android.app.Service
import _root_.android.content.{Context, Intent}
import _root_.android.location._
import _root_.android.os.{Bundle, IBinder, Handler}
import _root_.android.preference.PreferenceManager
import _root_.android.util.Log
import _root_.android.widget.Toast

object AprsService {
	val SERVICE = "de.duenndns.aprsdroid.SERVICE"
	val SERVICE_ONCE = "de.duenndns.aprsdroid.ONCE"
	val UPDATE = "de.duenndns.aprsdroid.UPDATE"
	val LOCATION = "de.duenndns.aprsdroid.LOCATION"
	val STATUS = "de.duenndns.aprsdroid.STATUS"
	val PACKET = "de.duenndns.aprsdroid.PACKET"

	var running = false

	implicit def block2runnable(block: => Unit) =
		new Runnable() {
			def run() { block }
		}

}

class AprsService extends Service with LocationListener {
	import AprsService._
	val TAG = "AprsService"

	lazy val prefs = PreferenceManager.getDefaultSharedPreferences(this)

	lazy val locMan = getSystemService(Context.LOCATION_SERVICE).asInstanceOf[LocationManager]

	val handler = new Handler()

	lazy val db = StorageDatabase.open(this)

	var poster : AprsIsUploader = null

	var singleShot = false
	var lastLoc : Location = null
	var awaitingSpdCourse : Location = null

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
		running = true

		// get update interval and distance
		val upd_int = prefs.getString("interval", "10").toInt
		val upd_dist = prefs.getString("distance", "10").toInt

		// display notification (even though we are not actually started yet,
		// but we need this to prevent error message reordering)
		awaitingSpdCourse = null
		if (i.getAction() == SERVICE_ONCE) {
			lastLoc = null // for singleshot mode, ignore last post
			singleShot = true
			showToast(getString(R.string.service_once))
		} else
			showToast(getString(R.string.service_start).format(upd_int, upd_dist))

		// the poster needs to be running before location updates come in
		startPoster()

		if (prefs.getBoolean("netloc", false)) {
			locMan.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
				upd_int * 60000, upd_dist * 1000, this)
		}
		locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER,
			upd_int * 60000, upd_dist * 1000, this)

		val callssid = AprsPacket.formatCallSsid(prefs.getString("callsign", null), prefs.getString("ssid", ""))
		val message = "%s: %d min, %d km".format(callssid, upd_int, upd_dist)
		ServiceNotifier.instance.start(this, message)
	}

	def startPoster() {
		var hostname = prefs.getString("host", null)
		if (hostname == null || hostname == "")
			hostname = getString(R.string.aprs_server);
		val login = AprsPacket.formatLogin(prefs.getString("callsign", null).trim(),
			prefs.getString("ssid", null), prefs.getString("passcode", null))
		val filterdist = prefs.getString("filterdist", "10").toInt

		prefs.getString("conntype", "http") match {
		case "udp" =>
			poster = new UdpUploader(hostname, login)
		case "http" =>
			poster = new HttpPostUploader(hostname, login)
		case "tcp" =>
			poster = new TcpUploader(this, hostname, login, " filter m/%d".format(filterdist))
		case "afsk" =>
			poster = new AfskUploader(hostname, login)
		case _ =>
			stopSelf()
		}
		poster.start()
	}

	override def onBind(i : Intent) : IBinder = null
		
	override def onUnbind(i : Intent) : Boolean = false
		
	def showToast(msg : String) {
		Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
		addPost(StorageDatabase.Post.TYPE_INFO, null, msg)
	}

	override def onDestroy() {
		locMan.removeUpdates(this);
		// catch FC when service is killed from outside
		if (poster != null) {
			poster.stop()
			showToast(getString(R.string.service_stop))
		}
		ServiceNotifier.instance.stop(this)
		running = false
	}

	def speedBearingStart() {
		Log.d(TAG, "switching to fast lane");
		// request fast update rate
		locMan.removeUpdates(this);
		locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER,
			0, 0, this)
		handler.postDelayed({ speedBearingEnd(true) }, 30000)
	}

	def speedBearingEnd(post : Boolean) {
		if (!running)
			return;
		Log.d(TAG, "switching to slow lane");
		if (post && awaitingSpdCourse != null) {
			Log.d(TAG, "speedBearingEnd: posting " + awaitingSpdCourse);
			postLocation(awaitingSpdCourse)
		}
		awaitingSpdCourse = null
		// reset update speed
		locMan.removeUpdates(this);
		val upd_int = prefs.getString("interval", "10").toInt
		val upd_dist = prefs.getString("distance", "10").toInt
		locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER,
			upd_int, upd_dist, this)
	}

	def checkSpeedBearing(location : Location) : Boolean = {
		val hasSpdBrg = (location.hasBearing && location.hasSpeed)
		if (!hasSpdBrg) {
			if (awaitingSpdCourse == null)
				speedBearingStart()
			awaitingSpdCourse = location
			return false
		} else if (awaitingSpdCourse != null && hasSpdBrg) {
			speedBearingEnd(false)
		}
		true
	}

	// LocationListener interface
	override def onLocationChanged(location : Location) {
		Log.d(TAG, "onLocationChanged: " + location)
		val upd_int = prefs.getString("interval", "10").toInt * 60000
		val upd_dist = prefs.getString("distance", "10").toInt * 1000
		if (lastLoc != null &&
		    (location.getTime - lastLoc.getTime < upd_int ||
		     location.distanceTo(lastLoc) < upd_dist)) {
			Log.d(TAG, "onLocationChanged: ignoring premature location")
			return
		}
		// check if we have speed and course
		val speedbrg = prefs.getBoolean("speedbrg", false)
		if (speedbrg && location.getProvider == LocationManager.GPS_PROVIDER) {
			if (!checkSpeedBearing(location))
				return
		}
		postLocation(location)
	}

	def postLocation(location : Location) {
		lastLoc = location

		val i = new Intent(UPDATE)
		i.putExtra(LOCATION, location)

		val callsign = prefs.getString("callsign", null).trim()
		val callssid = AprsPacket.formatCallSsid(callsign, prefs.getString("ssid", ""))
		var symbol = prefs.getString("symbol", "")
		if (symbol.length != 2)
			symbol = getString(R.string.default_symbol)
		val status = prefs.getString("status", getString(R.string.default_status))
		val packet = AprsPacket.formatLoc(callssid, symbol, status, location)

		Log.d(TAG, "packet: " + packet)
		val result = try {
			val status = poster.update(packet)
			i.putExtra(STATUS, status)
			i.putExtra(PACKET, packet)
			val prec_status = "%s (±%dm)".format(status, location.getAccuracy.asInstanceOf[Int])
			addPost(StorageDatabase.Post.TYPE_POST, prec_status, packet)
			prec_status
		} catch {
			case e : Exception =>
				i.putExtra(PACKET, e.getMessage())
				addPost(StorageDatabase.Post.TYPE_ERROR, "Error", e.getMessage())
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
	}
	override def onProviderEnabled(provider : String) {
		Log.d(TAG, "onProviderEnabled: " + provider)
	}
	override def onStatusChanged(provider : String, st: Int, extras : Bundle) {
		Log.d(TAG, "onStatusChanged: " + provider)
	}

	def addPost(t : Int, status : String, message : String) {
		db.addPost(System.currentTimeMillis(), t, status, message)
		sendBroadcast(new Intent(UPDATE).putExtra(STATUS, message))
	}

	def postSubmit(post : String) {
		handler.post { addPost(StorageDatabase.Post.TYPE_INCMG, "incoming", post) }
	}

	def postAbort(post : String) {
		handler.post {
			addPost(StorageDatabase.Post.TYPE_ERROR, "Error", post)
			stopSelf()
		}
	}
}

