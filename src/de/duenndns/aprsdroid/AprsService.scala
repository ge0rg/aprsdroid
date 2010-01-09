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

	override def onStart(i : Intent, startId : Int) {
		super.onStart(i, startId)
		running = true

		val upd_int = prefs.getString("interval", "10").toInt
		val upd_dist = prefs.getString("distance", "10").toInt
		locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER,
			upd_int * 60000, upd_dist * 1000, this)

		if (i.getAction() == SERVICE_ONCE) {
			singleShot = true
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
		val i = new Intent(UPDATE)
		i.putExtra(LOCATION, location)
		val callsign = prefs.getString("callsign", null)
		val callssid = AprsPacket.formatCallSsid(callsign, prefs.getString("ssid", ""))
		val status = prefs.getString("status", "http://github.com/ge0rg/aprsdroid")
		val packet = AprsPacket.formatLoc(callssid, status, location)
		Log.d(TAG, "packet: " + packet)
		try {
			if (prefs.getString("conntype", "udp") == "udp")
				new AprsUdp(prefs).update(packet)
			else
				new AprsHttpPost(prefs).update(packet)
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

