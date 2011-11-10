package org.aprsdroid.app

import _root_.android.content.Context
import _root_.android.location.Location
import _root_.android.os.{Bundle, Handler}
import _root_.android.util.Log

class FixedPosition(service : AprsService, prefs : PrefsWrapper) extends LocationSource {
	val TAG = "APRSdroid.FixedPosition"
	val periodicPoster = new Runnable() { override def run() { postPosition(); postRefresh(); } }
	//val handler = new Handler(service)

	override def start(singleShot : Boolean) {
		postPosition()
		if (!singleShot)
			postRefresh()
	}

	override def restart() {
		stop()
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
		service.postLocation(location)
	}
}
