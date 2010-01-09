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

	def splitCoord(c : Double) : (Int, Double, Int) = {
		var deg = c.asInstanceOf[Int]
		val min = (c - deg)*60
		var letter = 0
		if (deg < 0) {
			deg = -deg
			letter = 1
		}
		(deg, min, letter)
	}
		
	def formatLat(c : Double) : String = {
		val (deg, min, letter) = splitCoord(c)
		"%02d%05.2f%c".format(deg, min, "NS"(letter))
	}
	def formatLon(c : Double) : String = {
		val (deg, min, letter) = splitCoord(c)
		"%03d%05.2f%c".format(deg, min, "EW"(letter))
	}

	def formatLoc(callsign : String, location : Location) : String = {
		callsign + ">APAND1,TCPIP*:!" + formatLat(location.getLatitude) + "/" +
			formatLon(location.getLongitude) + "$ http://github.com/ge0rg/aprsdroid"
	}

	def formatLogin(callsign : String, passcode : String) : String = {
		"user " + callsign.split("-")(0) + " pass " + passcode + " vers APRSdroid 0.1"
	}
}
