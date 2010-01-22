package de.duenndns.aprsdroid

import _root_.android.app.Service
import _root_.android.content.{Context, Intent}
import _root_.android.location._
import _root_.android.os.{Bundle, IBinder}
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

	var singleShot = false
	var lastTime : Long = 0

	override def onStart(i : Intent, startId : Int) {
		super.onStart(i, startId)
		running = true

		val upd_int = prefs.getString("interval", "10").toInt
		val upd_dist = prefs.getString("distance", "10").toInt
		locMan.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
			upd_int * 60000, upd_dist * 1000, this)
		locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER,
			upd_int * 60000, upd_dist * 1000, this)

		if (i.getAction() == SERVICE_ONCE) {
			singleShot = true
			lastTime = 0 // for singleshot mode, ignore last post
			showToast(getString(R.string.service_once))
		} else
			showToast(getString(R.string.service_start).format(upd_int, upd_dist))
	}

	override def onBind(i : Intent) : IBinder = null
		
	override def onUnbind(i : Intent) : Boolean = false
		
	def showToast(msg : String) {
		Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
		sendBroadcast(new Intent(UPDATE).putExtra(STATUS, msg))
	}

	override def onDestroy() {
		locMan.removeUpdates(this);
		running = false
		showToast(getString(R.string.service_stop))
	}

	// LocationListener interface
	override def onLocationChanged(location : Location) {
		Log.d(TAG, "onLocationChanged: " + location)
		val upd_int = (prefs.getString("interval", "10").toInt) * 60000
		if (location.getTime < lastTime + upd_int) {
			Log.d(TAG, "onLocationChanged: ignoring premature location")
			return
		}
		lastTime = location.getTime

		val i = new Intent(UPDATE)
		i.putExtra(LOCATION, location)

		val callsign = prefs.getString("callsign", null)
		val callssid = AprsPacket.formatCallSsid(callsign, prefs.getString("ssid", ""))
		val status = prefs.getString("status", getString(R.string.default_status))

		val login = AprsPacket.formatLogin(prefs.getString("callsign", null),
			prefs.getString("ssid", null), prefs.getString("passcode", null))
		val packet = AprsPacket.formatLoc(callssid, status, location)

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
		} catch {
			case e : Exception => i.putExtra(PACKET, e.getMessage())
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

