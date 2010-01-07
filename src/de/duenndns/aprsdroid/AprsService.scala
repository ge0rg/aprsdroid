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
	val PACKET = "de.duenndns.aprsdroid.PACKET"

	var running = false
}

class AprsService extends Service with LocationListener {
	import AprsService._
	val TAG = "AprsService"

	lazy val prefs = PreferenceManager.getDefaultSharedPreferences(this)

	lazy val locMan = getSystemService(Context.LOCATION_SERVICE).asInstanceOf[LocationManager]

	override def onStart(i : Intent, startId : Int) {
		super.onStart(i, startId)

		showToast("Service started: " + i.getAction)
		i.getAction match {
		case SERVICE =>
			val upd_int = prefs.getInt("interval", 10)
			val upd_dist = prefs.getInt("distance", 10)
			locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER,
				upd_int * 60000, upd_dist * 1000, this)
			running = true
		case SERVICE_ONCE =>
			stopSelf()
		}
	}

	override def onBind(i : Intent) : IBinder = null
		
	override def onUnbind(i : Intent) : Boolean = false
		
	def showToast(msg : String) {
		Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
	}

	override def onDestroy() {
		showToast("APRS Service stopped.")
		locMan.removeUpdates(this);
		running = false
	}

	// LocationListener interface
	override def onLocationChanged(location : Location) {
		Log.d(TAG, "onLocationChanged: " + location)
		val i = new Intent(UPDATE)
		i.putExtra(LOCATION, location)
		val packet = AprsPacket.formatLoc(prefs.getString("callsign", null), location)
		try {
			new AprsHttpPost(prefs).update(packet)
			i.putExtra(PACKET, packet)
		} catch {
			case e : Exception => i.putExtra(PACKET, e.getMessage())
		}
		sendBroadcast(i)
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

