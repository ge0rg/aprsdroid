package org.aprsdroid.app

import _root_.android.location.Location
import _root_.net.ab0oo.aprs.parser._

object AprsPacket {
	val QRG_RE = ".*?(\\d{2,3}[.,]\\d{3,4}).*?".r

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
	def passcodeAllowed(callssid : String, pass : String, optional : Boolean) = {
		pass match {
		case "" => optional
		case "-1" => optional
		case _ => (passcode(callssid).toString() == pass)
		}
	}
		

	def formatCallSsid(callsign : String, ssid : String) : String = {
		if (ssid != null && ssid != "")
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

	def formatFreq(csespd : String, freq : Float) : String = {
		if (freq == 0) "" else {
			val prefix = if (csespd.length() > 0) "/" else ""
			prefix + "%07.3fMHz".formatLocal(null, freq)
		}
	}

	def formatLogin(callsign : String, ssid : String, passcode : String, version : String) : String = {
		"user %s pass %s vers %s".format(formatCallSsid(callsign, ssid), passcode, version)
	}

	def formatRangeFilter(loc : Location, range : Int) : String = {
		if (loc != null)
			"r/%1.3f/%1.3f/%d".formatLocal(null, loc.getLatitude, loc.getLongitude, range)
		else
			""
	}

	def parseQrg(comment : String) : String = {
		comment match {
		case QRG_RE(qrg) => qrg
		case _ => null
		}
	}

	def parseHostPort(hostport : String, defaultport : Int) : (String, Int) = {
		val splits = hostport.trim().split(":")
		try {
			// assume string:int
			return (splits(0), splits(1).toInt)
		} catch {
			// fallback to default port if none/bad one given
			case _ => return (splits(0), defaultport)
		}
	}

	// position ambiguity re-defined as 67% (Android's Location)
	// of the worst-case error from the ambiguity field
	//
	// Best possible APRS precision at the equator is ~18m, we assume
	// proper rounding (so max. 9m between actual and reported position)
	// and take 67% of that.
	val APRS_AMBIGUITY_METERS = Array(6, 37185, 6200, 620, 62)

	def position2location(ts : Long, p : Position, cse : CourseAndSpeedExtension = null) = {
		val l = new Location("APRS")
		l.setLatitude(p.getLatitude())
		l.setLongitude(p.getLongitude())
		l.setTime(ts)
		l.setAccuracy(APRS_AMBIGUITY_METERS(p.getPositionAmbiguity()))
		if (cse != null) {
			// course == bearing?
			l.setBearing(cse.getCourse)
			// APRS uses knots, Location expects m/s
			l.setSpeed(cse.getSpeed / 1.94384449f)
		}
		// todo: bearing, speed
		l
	}
}
