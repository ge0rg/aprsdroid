package org.aprsdroid.app

import _root_.android.content.Context
import _root_.android.location._
import _root_.android.util.Log
import _root_.android.os.{Bundle, Handler, Looper}
import _root_.java.io.{BufferedReader, InputStream, InputStreamReader, OutputStream, OutputStreamWriter}

import _root_.net.ab0oo.aprs.parser._

class KenwoodProto(service : AprsService, is : InputStream, os : OutputStream) extends TncProto(is, null) {
	val TAG = "APRSdroid.KenwoodProto"
	val br = new BufferedReader(new InputStreamReader(is))
	val sinkhole = new LocationSinkhole()
	val locMan = service.getSystemService(Context.LOCATION_SERVICE).asInstanceOf[LocationManager]
	val output = new OutputStreamWriter(os)

	var listener : NmeaListener = null
	new Handler(Looper.getMainLooper()).post(new Runnable() { override def run() {
		listener = new NmeaListener()
		locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER,
			0, 0, sinkhole)
		locMan.addNmeaListener(listener)
	}})

	def wpl2aprs(line : String) = {
		val s = line.split("[,*]") // get and split nmea
		s(0) match {
		case "$PKWDWPL" =>
			val lat = "%s%s".format(s(3), s(4))
			val lon = "%s%s".format(s(5), s(6))
			val call = s(11).trim()
			val sym = s(12)
			"%s>APRS:!%s%s%s%s".format(call, lat, sym(0), lon, sym(1))
		case "$GPWPL" =>
			val lat = "%s%s".format(s(1), s(2))
			val lon = "%s%s".format(s(3), s(4))
			val call = s(5).trim()
			"%s>APRS:!%s/%s/".format(call, lat, lon)
		case _ => line
		}
	}

	def readPacket() : String = {
		val line = br.readLine()
		Log.d(TAG, "got " + line)
		return wpl2aprs(line)
	}

	def writePacket(p : APRSPacket) {
		// don't do anything. yet.
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

	class LocationSinkhole extends LocationListener {
	override def onLocationChanged(location : Location) {
	}

	override def onProviderDisabled(provider : String) {
	}
	override def onProviderEnabled(provider : String) {
	}
	override def onStatusChanged(provider : String, st: Int, extras : Bundle) {
	}
	}

	override def stop() {
		locMan.removeUpdates(sinkhole)
		locMan.removeNmeaListener(listener)
		super.stop()
	}
}

