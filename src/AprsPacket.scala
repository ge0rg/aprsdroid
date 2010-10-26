package de.duenndns.aprsdroid

import scala.util.matching.Regex
import _root_.android.location.Location

object AprsPacket {

	// position report regex:
	// #0: call #1: latitude  #2: sym1  #3: longitude  #4: sym2  #5: comment
	val POS_RE = """([A-Z0-9-]+)>.*[!=/zh](\d{4}\.\d{2}[NS])(.)(\d{5}\.\d{2}[EW])(.)\s*(.*)"""
	lazy val PositionRegex = new Regex(POS_RE)
	lazy val CoordRegex = new Regex("""(\d{2,3})(\d{2})\.(\d{2})([NSEW])""")

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
		var letter = 0
		var minDec = (c*6000).asInstanceOf[Int]
		if (minDec < 0) {
			minDec = -minDec
			letter = 1
		}
		var deg = minDec / 6000
		val min = (minDec / 100) % 60
		val minFrac = minDec % 100
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

	def coord2microdeg(s : String) : Int = {
		val CoordRegex(captures @ _*) = s
		val Seq(deg, min, minFrac) = captures.take(3).map(_.toInt)
		val nsew = captures.last(0)
		// .d = M.m/60 = (M + (m/100))/60
		val microdeg = deg*1000000 + 10000*(min*100 + minFrac)/60
		nsew match {
		case 'N' => microdeg
		case 'E' => microdeg
		case 'S' => -microdeg
		case 'W' => -microdeg
		}
	}

	def parseReport(report : String) : (String, Int, Int, String, String) = {
		val PositionRegex(call, lat, sym1, lon, sym2, comment) = report
		(call, coord2microdeg(lat), coord2microdeg(lon), sym1+sym2, comment)
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

	def parseHostPort(hostport : String, defaultport : Int) : (String, Int) = {
		val splits = hostport.split(":")
		if (splits.length == 2)
			return (splits(0), splits(1).toInt)
		else
			return (splits(0), defaultport)
	}
}
