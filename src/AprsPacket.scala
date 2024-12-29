package org.aprsdroid.app

import _root_.android.location.Location
import _root_.net.ab0oo.aprs.parser._

import scala.math.abs

object AprsPacket {
	val QRG_RE = ".*?(\\d{2,3}[.,]\\d{3,4}).*?".r
	  val characters = Array(
		"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D",
		"E", "F", "G", "H", "I", "J", "K", "L", "P", "Q", "R", "S", "T", "U", "V", 
		"W", "X", "Y", "Z"
	  )

	  def statusToBits(status: String): (Int, Int, Int) = status match {
		case "Off Duty" => (1, 1, 1)
		case "En Route" => (1, 1, 0)
		case "In Service" => (1, 0, 1)
		case "Returning" => (1, 0, 0)
		case "Committed" => (0, 1, 1)
		case "Special" => (0, 1, 0)
		case "Priority" => (0, 0, 1)
		case "EMERGENCY!" => (0, 0, 0)
		case _ => (1, 1, 1) // Default if status is not found
	  }

	  def degreesToDDM(dd: Double): (Int, Double) = {
		val degrees = Math.floor(dd).toInt
		val minutes = (dd - degrees) * 60
		(degrees, minutes)
	  }

	  def miceLong(dd: Double): (Int, Int, Int) = {
		val (degrees, minutes) = degreesToDDM(Math.abs(dd))
		val minutesInt = Math.floor(minutes).toInt
		val minutesHundreths = Math.floor(100 * (minutes - minutesInt)).toInt
		(degrees, minutesInt, minutesHundreths)
	  }


	  def encodeDest(dd: Double, longOffset: Int, west: Int, messageA: Int, messageB: Int, messageC: Int, ambiguity: Int): String = {
	    val north = if (dd < 0) 0 else 1
	  
	    val (degrees, minutes, minutesHundreths) = miceLong(dd)
	  
	    val degrees10 = Math.floor(degrees / 10.0).toInt
	    val degrees1 = degrees - (degrees10 * 10)
	  
	    val minutes10 = Math.floor(minutes / 10.0).toInt
	    val minutes1 = minutes - (minutes10 * 10)
	  
	    val minutesHundreths10 = Math.floor(minutesHundreths / 10.0).toInt
	    val minutesHundreths1 = minutesHundreths - (minutesHundreths10 * 10)
	  
	    val sb = new StringBuilder
	  	
	    if (messageA == 1) sb.append(characters(degrees10 + 22)) else sb.append(characters(degrees10))
	    if (messageB == 1) sb.append(characters(degrees1 + 22)) else sb.append(characters(degrees1))
	    if (messageC == 1) sb.append(characters(minutes10 + 22)) else sb.append(characters(minutes10))
	  	  
	    if (north == 1) sb.append(characters(minutes1 + 22)) else sb.append(characters(minutes1))
	    if (longOffset == 1) sb.append(characters(minutesHundreths10 + 22)) else sb.append(characters(minutesHundreths10))
	    if (west == 1) sb.append(characters(minutesHundreths1 + 22)) else sb.append(characters(minutesHundreths1))
	  
	    val encoded = sb.toString()
	  
	    // Replace indices 4 and 5 with 'Z' or 'L', depending on 'west'
	    val validAmbiguity = ambiguity.max(0).min(4)
	    val encodedArray = encoded.toCharArray // Convert the encoded string to a char array
	  
	    // A map that specifies the modification rules for each index based on ambiguity
	    val modifyRules = Map(
	  	2 -> (messageC, 'Z', 'L'),
	  	3 -> (north, 'Z', 'L'),
	  	4 -> (longOffset, 'Z', 'L'),
	  	5 -> (west, 'Z', 'L')
	    )
	  
	    // Loop over the indices based on validAmbiguity
	    for (i <- (6 - validAmbiguity) until 6) {
	  	modifyRules.get(i) match {
	  	  case Some((condition, trueChar, falseChar)) =>
	  		val charToUse = if (condition == 1) trueChar else falseChar
	  		encodedArray(i) = charToUse
	  	  case None => // No modification if the index is not in modifyRules
	  	}
	    }
	  
	    // Return the modified string
	    val finalEncoded = new String(encodedArray)
	  
	    finalEncoded
	  }

	  def encodeInfo(dd: Double, speed: Double, heading: Double, symbol: String): (String, Int, Int) = {
	  
		val (degrees, minutes, minutesHundreths) = miceLong(dd)

		val west = if (dd < 0) 1 else 0

		val sb = new StringBuilder
		sb.append("`")

		val speedHT = Math.floor(speed / 10.0).toInt
		val speedUnits = speed - (speedHT * 10)

		val headingHundreds = Math.floor(heading / 100.0).toInt
		val headingTensUnits = heading - (headingHundreds * 100)

		var longOffset = 0

		if (degrees <= 9) {
		  sb.append((degrees + 118).toChar)
		  longOffset = 1
		} else if (degrees >= 10 && degrees <= 99) {
		  sb.append((degrees + 28).toChar)
		  longOffset = 0
		} else if (degrees >= 100 && degrees <= 109) {
		  sb.append((degrees + 8).toChar)
		  longOffset = 1
		} else if (degrees >= 110) {
		  sb.append((degrees - 72).toChar)
		  longOffset = 1
		}

		if (minutes <= 9) sb.append((minutes + 88).toChar) else sb.append((minutes + 28).toChar)
		sb.append((minutesHundreths + 28).toChar)

		if (speed <= 199) sb.append((speedHT + 108).toChar) else sb.append((speedHT + 28).toChar)
		sb.append((Math.floor(speedUnits * 10).toInt + headingHundreds + 32).toChar)
		sb.append((headingTensUnits + 28).toChar)

		sb.append(symbol(1))
		sb.append(symbol(0))
		sb.append("`")

		(sb.toString(), west, longOffset)
	  }

	  def altitude(alt: Double): String = {
		val altM = Math.round(alt * 0.3048).toInt
		val relAlt = altM + 10000

		val val1 = Math.floor(relAlt / 8281.0).toInt
		val rem = relAlt % 8281
		val val2 = Math.floor(rem / 91.0).toInt
		val val3 = rem % 91

		// Ensure that the characters are treated as strings and concatenate properly
	    charFromInt(val1).toString + charFromInt(val2).toString + charFromInt(val3).toString + "}"
		}

	private def charFromInt(value: Int): Char = (value + 33).toChar

	def formatCourseSpeedMice(location: Location): (Int, Int) = {
	  // Default values
	  val status_spd = if (location.hasSpeed && location.getSpeed > 2) {
		// Convert speed from m/s to knots, and return as an integer
		mps2kt(location.getSpeed).toInt
	  } else {
		0 // If no valid speed or below threshold, set speed to 0
	  }

	  val course = if (location.hasBearing) {
		// Get bearing as an integer (course)
		location.getBearing.asInstanceOf[Int]
	  } else {
		0 // If no bearing, set course to 0
	  }

	  (status_spd, course)
	}

	def formatAltitudeMice(location: Location): Option[Int] = {
	  if (location.hasAltitude) {
		// Convert altitude to feet, round to nearest integer, and wrap in Some
		Some(math.round(m2ft(location.getAltitude)).toInt)
	  } else {
		None // If no altitude, return None
	  }
	}

	def passcode(callssid : String) : Int = {
		// remove ssid, uppercase, add \0 for odd-length calls
		val call = callssid.split("-")(0).toUpperCase() + "\u0000"
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
			"/A=%06d".formatLocal(null, m2ft(location.getAltitude))
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
			"%03d/%03d".formatLocal(null, location.getBearing.asInstanceOf[Int],
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
	
	def formatFreqMice(freq : Float) : String = {
		if (freq == 0) "" else {
			"%07.3fMHz".formatLocal(null, freq)
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

	val  DirectionsLatitude = "NS";
	val  DirectionsLongitude = "EW";
	def formatDMS(coordinate : Float, nesw : String) = {
		val dms = Location.convert(abs(coordinate), Location.FORMAT_SECONDS).split(":")
		val nesw_idx = (coordinate < 0).compare(false)
		"%2s° %2s' %s\" %s".format(dms(0), dms(1), dms(2), nesw(nesw_idx))
	}

	def formatCoordinates(latitude : Float, longitude : Float) = {
		(AprsPacket.formatDMS(latitude, DirectionsLatitude),
		 AprsPacket.formatDMS(longitude, DirectionsLongitude))
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
			case _ : Throwable => return (splits(0), defaultport)
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
