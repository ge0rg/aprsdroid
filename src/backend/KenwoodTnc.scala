package org.aprsdroid.app

import _root_.android.content.Context
import _root_.android.location.{GpsStatus, LocationManager}
import _root_.android.util.Log

import _root_.java.io.{InputStream, OutputStream, OutputStreamWriter, PrintWriter}

class KenwoodTnc(service : AprsService, prefs : PrefsWrapper) extends BluetoothTnc(service, prefs) {
	override val TAG = "APRSdroid.KenwoodTnc"

	val locMan = service.getSystemService(Context.LOCATION_SERVICE).asInstanceOf[LocationManager]
	var output : OutputStreamWriter = null

	var listener : NmeaListener = null
	if (android.os.Build.VERSION.SDK_INT >= 5) {
		listener = new NmeaListener()
		locMan.addNmeaListener(listener)
	}

	override def createTncProto(is : InputStream, os : OutputStream) = {
		output = new OutputStreamWriter(os)
		new KenwoodProto(is)
	}

	class NmeaListener extends GpsStatus.NmeaListener() {
	def onNmeaReceived(timestamp : Long, nmea : String) {
		if (output != null && (nmea.startsWith("$GPGGA") || nmea.startsWith("$GPRMC"))) {
			Log.d(TAG, "NMEA >>> " + nmea)
			try {
				output.write(nmea)
			} catch {
			case e : Exception =>
				Log.e(TAG, "error sending NMEA to Kenwood: " + e)
				e.printStackTrace()
			}
		} else
			Log.d(TAG, "NMEA --- " + nmea)
	}}

	override def stop() {
		if (android.os.Build.VERSION.SDK_INT >= 5)
			locMan.removeNmeaListener(listener)
		super.stop()
	}
}

