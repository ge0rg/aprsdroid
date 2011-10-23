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

	def formatLoc(callssid : String, toCall : String, symbol : String,
			status : String, location : Location) = {
		new APRSPacket(callssid, toCall, null, new PositionPacket(
			new Position(location.getLatitude, location.getLongitude, 0,
				     symbol(0), symbol(1)),
			formatCourseSpeed(location) + formatAltitude(location) +
			" " + status, /* messaging = */ true))
	}

	def formatMessage(callssid : String, toCall : String, dest : String,
			message : String, msgid : String) = {
		new APRSPacket(callssid, toCall, null, new MessagePacket(dest,
			message, msgid))
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
}
