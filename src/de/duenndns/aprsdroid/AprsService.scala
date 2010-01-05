package de.duenndns.aprsdroid

import _root_.android.app.Service
import _root_.android.content.{Context, Intent}
import _root_.android.location._
import _root_.android.os.{Bundle, IBinder}
import _root_.android.util.Log
import _root_.android.widget.Toast

object AprsService {
	val SERVICE = "de.duenndns.aprsdroid.SERVICE"
	val SERVICE_ONCE = "de.duenndns.aprsdroid.ONCE"
	val UPDATE = "de.duenndns.aprsdroid.UPDATE"
	val LOCATION = "de.duenndns.aprsdroid.LOCATION"
}

class AprsService extends Service with LocationListener {
	import AprsService._
	val TAG = "AprsService"

	val UPDATE_TIME = 10000 // 10k ms = 10s
	val UPDATE_DIST = 10 // 10m

	lazy val locMan = getSystemService(Context.LOCATION_SERVICE).asInstanceOf[LocationManager]

	override def onStart(i : Intent, startId : Int) {
		super.onStart(i, startId)

		showToast("Service started: " + i.getAction)
		i.getAction match {
		case SERVICE =>
			locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER,
				UPDATE_TIME, UPDATE_DIST, this)
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
	}

	// LocationListener interface
	override def onLocationChanged(location : Location) {
		Log.d(TAG, "onLocationChanged: " + location)
		val i = new Intent(UPDATE)
		i.putExtra(LOCATION, location)
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

