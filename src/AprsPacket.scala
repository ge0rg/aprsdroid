package de.duenndns.aprsdroid

import _root_.android.location.Location

object AprsPacket {

	def passcode(callssid : String) : Int = {
		// remove ssid, uppercase, add \0 for odd-length calls
		val call = callssid.split("-")(0).toUpperCase() + "\0"
		var hash = 0x73e2
		for (i <- 0 to call.length-2 by 2) {
			hash ^= call(i) << 8
			hash ^= call(i+1)
		}
		hash & 0x7fff
	}

	def splitCoord(c : Double) : (Int, Int, Int, Int) = {
		val minDec = (c*6000).asInstanceOf[Int]
		var deg = minDec / 6000
		val min = (minDec / 100) % 60
		val minFrac = minDec % 100
		var letter = 0
		if (deg < 0) {
			deg = -deg
			letter = 1
		}
		(deg, min, minFrac, letter)
	}
		
	def formatLat(c : Double) : String = {
		val (deg, min, minFrac, letter) = splitCoord(c)
		"%02d%02d.%02d%c".format(deg, min, minFrac, "NS"(letter))
	}
	def formatLon(c : Double) : String = {
		val (deg, min, minFrac, letter) = splitCoord(c)
		"%03d%02d.%02d%c".format(deg, min, minFrac, "EW"(letter))
	}

	def formatCallSsid(callsign : String, ssid : String) : String = {
		if (ssid != "")
			return callsign + "-" + ssid
		else
			return callsign
	}

	def m2ft(meter : Double) : Int = (meter*3.2808399).asInstanceOf[Int]

	def mps2kt(mps : Double) : Int = (mps*1.94384449).asInstanceOf[Int]

	def formatAltitude(location : Location) : String = {
		if (location.hasAltitude)
			"/A=%06d".format(m2ft(location.getAltitude))
		else
			""
	}

	def formatCourseSpeed(location : Location) : String = {
		// only report speeds above 2m/s (7.2km/h)
		if (location.hasSpeed && location.hasBearing)
		   // && location.getSpeed > 2)
			"%03d/%03d".format(location.getBearing.asInstanceOf[Int],
				mps2kt(location.getSpeed))
		else
			""
	}

	def formatLoc(callssid : String, symbol : String,
			status : String, location : Location) : String = {
		callssid + ">APAND1,TCPIP*:!" + formatLat(location.getLatitude) +
			symbol(0) + formatLon(location.getLongitude) + symbol(1) +
			formatCourseSpeed(location) + formatAltitude(location) +
			" " + status
	}

	def formatLogin(callsign : String, ssid : String, passcode : String) : String = {
		"user " + formatCallSsid(callsign, ssid) + " pass " + passcode + " vers APRSdroid 0.1"
	}
}
