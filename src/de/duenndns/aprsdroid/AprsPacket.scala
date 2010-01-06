package de.duenndns.aprsdroid

import _root_.android.location.Location

object AprsPacket {
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
		callsign + ">APRS:!" + formatLat(location.getLatitude) + "/" +
			formatLon(location.getLongitude)
	}
}
