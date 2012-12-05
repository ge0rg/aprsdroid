package org.aprsdroid.app

import _root_.android.content.Context
import _root_.android.location.{GpsStatus, LocationManager}
import _root_.android.util.Log

import _root_.java.io.{InputStream, OutputStream, OutputStreamWriter, PrintWriter}

class KenwoodTnc(service : AprsService, prefs : PrefsWrapper) extends BluetoothTnc(service, prefs)
		with GpsStatus.NmeaListener {
	override val TAG = "APRSdroid.KenwoodTnc"

	val locMan = service.getSystemService(Context.LOCATION_SERVICE).asInstanceOf[LocationManager]
	var output : OutputStreamWriter = null

	locMan.addNmeaListener(this)

	override def createTncProto(is : InputStream, os : OutputStream) = {
		output = new OutputStreamWriter(os)
		new KenwoodProto(is)
	}

	def onNmeaReceived(timestamp : Long, nmea : String) {
		Log.d(TAG, "NMEA <<< " + nmea)
		if (output != null)
			output.write(nmea)
	}

	override def stop() {
		locMan.removeNmeaListener(this)
		super.stop()
	}
}

