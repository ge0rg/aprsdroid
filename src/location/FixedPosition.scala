package org.aprsdroid.app

import _root_.android.content.Context
import _root_.android.location.Location
import _root_.android.os.{Bundle, Handler}
import _root_.android.util.Log

class FixedPosition(service : AprsService, prefs : PrefsWrapper) extends LocationSource {
	val TAG = "APRSdroid.FixedPosition"
	val periodicPoster = new Runnable() { override def run() { postPosition(); postRefresh(); } }

	override def start(singleShot : Boolean) = {
		stop()
		postPosition()
		if (!singleShot)
			postRefresh()

		service.getString(R.string.p_source_manual)
	}

	override def stop() {
		service.handler.removeCallbacks(periodicPoster)
	}

	def postRefresh() {
		// get update interval
		val upd_int = prefs.getStringInt("interval", 10)
		service.handler.postDelayed(periodicPoster, upd_int*60*1000)
	}

	def postPosition() {
		val location = new Location("manual")
		location.setLatitude(prefs.getStringFloat("manual_lat", 0))
		location.setLongitude(prefs.getStringFloat("manual_lon", 0))
		service.postLocation(location)
	}
}
