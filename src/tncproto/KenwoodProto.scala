package org.aprsdroid.app

import _root_.android.util.Log
import _root_.java.io.{BufferedReader, InputStream, InputStreamReader, OutputStream}

import _root_.net.ab0oo.aprs.parser._

class KenwoodProto(is : InputStream) extends TncProto(is, null) {
	val TAG = "APRSdroid.KenwoodProto"
	val br = new BufferedReader(new InputStreamReader(is))

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
}

