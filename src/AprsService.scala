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
}

class AprsService extends Service with LocationListener {
	import AprsService._
	val TAG = "AprsService"

	lazy val prefs = PreferenceManager.getDefaultSharedPreferences(this)

	lazy val locMan = getSystemService(Context.LOCATION_SERVICE).asInstanceOf[LocationManager]

	lazy val handler = new Handler()

	lazy val db = StorageDatabase.open(this)

	var singleShot = false
	var lastLoc : Location = null
	var awaitingSpdCourse : Location = null

	override def onStart(i : Intent, startId : Int) {
		Log.d(TAG, "onStart: " + i + ", " + startId);
		super.onStart(i, startId)
		running = true

		val upd_int = prefs.getString("interval", "10").toInt
		val upd_dist = prefs.getString("distance", "10").toInt
		if (prefs.getBoolean("netloc", false)) {
			locMan.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
				upd_int * 60000, upd_dist * 1000, this)
		}
		locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER,
			upd_int * 60000, upd_dist * 1000, this)

		awaitingSpdCourse = null
		if (i.getAction() == SERVICE_ONCE) {
			lastLoc = null // for singleshot mode, ignore last post
			singleShot = true
			showToast(getString(R.string.service_once))
		} else
			showToast(getString(R.string.service_start).format(upd_int, upd_dist))
	}

	override def onBind(i : Intent) : IBinder = null
		
	override def onUnbind(i : Intent) : Boolean = false
		
	def showToast(msg : String) {
		Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
		db.addPost(System.currentTimeMillis(), StorageDatabase.Post.TYPE_INFO, null, msg)
		sendBroadcast(new Intent(UPDATE).putExtra(STATUS, msg))
	}

	override def onDestroy() {
		locMan.removeUpdates(this);
		running = false
		showToast(getString(R.string.service_stop))
	}

	def speedBearingStart() {
		Log.d(TAG, "switching to fast lane");
		// request fast update rate
		locMan.removeUpdates(this);
		locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER,
			0, 0, this)
		handler.postDelayed(new Runnable() {
			def run() { speedBearingEnd(true) }
		}, 30000)
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
			false
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

		val callsign = prefs.getString("callsign", null)
		val callssid = AprsPacket.formatCallSsid(callsign, prefs.getString("ssid", ""))
		var symbol = prefs.getString("symbol", "")
		if (symbol.length != 2)
			symbol = getString(R.string.default_symbol)
		val status = prefs.getString("status", getString(R.string.default_status))

		val login = AprsPacket.formatLogin(prefs.getString("callsign", null),
			prefs.getString("ssid", null), prefs.getString("passcode", null))
		val packet = AprsPacket.formatLoc(callssid, symbol, status, location)

		var hostname = prefs.getString("host", null)
		if (hostname == null || hostname == "")
			hostname = getString(R.string.aprs_server);

		Log.d(TAG, "packet: " + packet)
		try {
			var poster : AprsIsUploader = null
			if (prefs.getString("conntype", "http") == "udp")
				poster = new AprsUdp()
			else
				poster = new AprsHttpPost()
			val status = poster.update(hostname, login, packet)
			i.putExtra(STATUS, status)
			i.putExtra(PACKET, packet)
			val prec_status = "%s (±%dm)".format(status, location.getAccuracy.asInstanceOf[Int])
			db.addPost(System.currentTimeMillis(), StorageDatabase.Post.TYPE_POST, prec_status, packet)
		} catch {
			case e : Exception =>
				i.putExtra(PACKET, e.getMessage())
				db.addPost(System.currentTimeMillis(), StorageDatabase.Post.TYPE_ERROR, "Error", e.getMessage())
		}
		sendBroadcast(i)
		if (singleShot) {
			singleShot = false
			stopSelf()
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

}

