package org.aprsdroid.app

import _root_.android.location.Location
import _root_.net.ab0oo.aprs.parser._
import scala.util.matching

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

	def kt2mps(kt : Double) : Int = (kt/1.94384449).asInstanceOf[Int]

	def hi2mm(hi : Double) : Double = (hi*0.254)

	def f2c(f : Double) : Double = (f - 32d) * 5d / 9d

	def formatAltitude(location : Location) : String = {
		if (location.hasAltitude)
			"/A=%06d".format(m2ft(location.getAltitude))
		else
			""
	}

	def formatAltitudeCompressed(location : Location) : String = {
		if (location.hasAltitude) {
			var altitude = m2ft(location.getAltitude)
			var compressedAltitude = ((math.log(altitude) / math.log(1.002)) + 0.5).asInstanceOf[Int]
			var c = (compressedAltitude / 91).asInstanceOf[Byte] + 33
			var s = (compressedAltitude % 91).asInstanceOf[Byte] + 33
			// Negative altitudes cannot be expressed in base-91 and results in corrupt packets
			if(c < 33) c = 33
			if(s < 33) s = 33
			"%c%c".format(c.asInstanceOf[Char], s.asInstanceOf[Char])
		} else
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

	def formatCourseSpeedCompressed(location : Location) : String = {
		// only report speeds above 2m/s (7.2km/h)
		if (location.hasSpeed && location.hasBearing) {
			// && location.getSpeed > 2)
			var compressedBearing = (location.getBearing.asInstanceOf[Int] / 4).asInstanceOf[Int]
			var compressedSpeed = ((math.log(mps2kt(location.getSpeed)) / math.log(1.08)) - 1).asInstanceOf[Int]
			var c = compressedBearing.asInstanceOf[Byte] + 33;
			var s = compressedSpeed.asInstanceOf[Byte] + 33;
			// Negative speeds a courses cannot be expressed in base-91 and results in corrupt packets
			if(c < 33) c = 33
			if(s < 33) s = 33
			"%c%c".format(c.asInstanceOf[Char], s.asInstanceOf[Char])
		} else {
			""
		}
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

	val WX_PEET_RE = "^\\$ULTW".r
	def parseWxPeet(comment : String) : String = {
		import StationListAdapter._
		""
	}


	def parseWxElement(element : String, expr : scala.util.matching.Regex, grp : Int) : Option[Double] = {
		var retval = expr.findFirstMatchIn(element).map(_ group grp).getOrElse(null)
		try { Some(retval.toDouble) } catch { case _ => None }
	}

	def parseWx(comment : String) : String = {
		import StationListAdapter._
		// Sample
		// 000/000g000t056r000p000P000h75b10164L000eCumulusDsVP
		//val comment = "000/000g000t056r000p000P000h75b10164L000eCumulusDsVP"

		// Handle PEET events
		if(comment.matches(WX_PEET_RE.toString)) return parseWxPeet(comment)

		val WX_WIND_RE = ".*(\\d{3})/(\\d{3}).*".r
		val WX_GUST_RE = ".*g(\\d{3}).*".r
		val WX_SPEED_RE = ".*s(\\d{3}).*".r
		val WX_DIR_RE = ".*c(\\d{3}).*".r
		val WX_TEMP_RE = ".*t(\\d{3}).*".r
		val WX_RAIN_HOUR_RE = ".*r(\\d{3}).*".r
		val WX_RAIN_24_RE = ".*p(\\d{3}).*".r
		val WX_RAIN_TODAY_RE = ".*P(\\d{3}).*".r
		val WX_HUMID_RE = ".*h(\\d{2}).*".r
		val WX_QNH_RE = ".*b(\\d{5}).*".r
		val WX_LUM_LOW_RE = ".*L(\\d{3}).*".r
		val WX_LUM_HIGH_RE = ".*l(\\d{4}).*".r
		val WX_SNOW = ".*s(\\d{3}).*".r

		var windDir = parseWxElement(comment, WX_WIND_RE, 1)
		var windSpeed = parseWxElement(comment, WX_WIND_RE, 2)
		var windGust = parseWxElement(comment, WX_GUST_RE, 1)
		var snow = parseWxElement(comment, WX_SNOW, 1)
		if (windSpeed == None) {
			// For some reason, snow and windspeed use the same prefix. If we didn't get wind using the
			// XXX/XXX format, then it must be here as sXXX wind speed,
			// therefore snow is not possible to get in this situation
			snow = None
			windSpeed = parseWxElement(comment, WX_SPEED_RE, 1)
		}
		if (windDir == None) windDir = parseWxElement(comment, WX_DIR_RE, 1)
		var ambTemp = parseWxElement(comment, WX_TEMP_RE, 1)
		var rainHour = parseWxElement(comment, WX_RAIN_HOUR_RE, 1)
		var rainDay = parseWxElement(comment, WX_RAIN_24_RE, 1)
		var rainToday = parseWxElement(comment, WX_RAIN_TODAY_RE, 1)
		var humidity = parseWxElement(comment, WX_HUMID_RE, 1)
		var qnh = parseWxElement(comment, WX_QNH_RE, 1)
		var lumLow = parseWxElement(comment, WX_LUM_LOW_RE, 1)
		var lumHigh = parseWxElement(comment, WX_LUM_HIGH_RE, 1)

		if(lumHigh != None)
			lumHigh = Some(lumHigh.get + 1000.0)

		var lum = if(lumLow != None) lumLow else if(lumHigh != None) lumHigh else None

		// unit conversions
		windSpeed = if (windSpeed != None) Some(kt2mps(windSpeed.get)) else None
		windGust = if (windGust != None) Some(kt2mps(windGust.get)) else None
		ambTemp =	if (ambTemp != None) Some(f2c(ambTemp.get)) else None
		rainHour = if(rainHour != None) Some(hi2mm(rainHour.get)) else None
		rainDay = if(rainDay != None) Some(hi2mm(rainDay.get)) else None
		rainToday = if(rainToday != None) Some(hi2mm(rainToday.get)) else None
		snow = if(snow != None) Some(hi2mm(snow.get)) else None
		var compWind = if (windDir != None) getBearing(windDir.get) else None

		val wxFinal = new scala.collection.mutable.ListBuffer[String]
		if(ambTemp != None) wxFinal +=  "%1.1f°C".format(ambTemp.get)
		if(humidity != None) wxFinal += "%1.0f%%".format(humidity.get)
		val windBuf = new scala.collection.mutable.ListBuffer[String]
		if(windSpeed != None) windBuf += "Wind %1.1fm/s".format(windSpeed.get)
		if(windGust != None) windBuf += "Gust %1.1fm/s".format(windGust.get)
		if(windDir != None) windBuf += "%s (%1.0f°)".format(compWind, windDir.get)
		if(windBuf.length > 0) wxFinal += windBuf.toList.mkString(" ")
		if(qnh != None) wxFinal += "%1.0fmbar".format(qnh.get)
		val rainBuf = new scala.collection.mutable.ListBuffer[String]
		if(rainHour != None) rainBuf += "%1.1f".format(rainHour.get)
		if(rainDay != None) rainBuf += "%1.1f".format(rainHour.get)
		if(rainToday != None) rainBuf += "%1.1f".format(rainHour.get)
		if(rainBuf.length > 0) wxFinal += "Rain " + rainBuf.toList.mkString("/") + "mm"
		if(snow != None) wxFinal += "Snow %1.1fmm".format(snow.get)
		if(lum != None) wxFinal += "%1.0fW/m2".format(lum.get)
		wxFinal.toList.mkString(" ")
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
