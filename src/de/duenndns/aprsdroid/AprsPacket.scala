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

	def formatLoc(callssid : String, status : String, location : Location) : String = {
		callssid + ">APAND1,TCPIP*:!" + formatLat(location.getLatitude) + "/" +
			formatLon(location.getLongitude) + "$ " + status
	}

	def formatLogin(callsign : String, ssid : String, passcode : String) : String = {
		"user " + formatCallSsid(callsign, ssid) + " pass " + passcode + " vers APRSdroid 0.1"
	}
}
