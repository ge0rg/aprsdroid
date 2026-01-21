package org.aprsdroid.app

import _root_.android.content.Context
import _root_.android.location._
import _root_.android.os.{Bundle, Handler}
import _root_.android.util.Log
import _root_.android.widget.Toast

class SmartBeaconing(service : AprsService, prefs : PrefsWrapper) extends LocationSource
		with LocationListener {
	val TAG = "APRSdroid.SmartBeaconing"
	
	lazy val locMan = service.getSystemService(Context.LOCATION_SERVICE).asInstanceOf[LocationManager]

	var lastLoc : Location = null
	var started = false

	def start(singleShot : Boolean) = {
		lastLoc = null
		if (!started) try {
			locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER,
				0, 0, this)
			started = true
		} catch {
			case e @ (_: IllegalArgumentException | _: SecurityException) =>
				// we lack GPS or GPS permissions
				service.postAbort(service.getString(R.string.service_sm_no_gps)
					+ "\n" + e.getMessage())
		}
		service.getString(R.string.p_source_smart)
	}

	def stop() {
		if (started)
			locMan.removeUpdates(this)
		started = false
	}

	def smartBeaconSpeedRate(speed : Float) : Int = {
		val SB_FAST_SPEED = prefs.getStringInt("sb.fastspeed", 100)/3.6 // [m/s]
		val SB_FAST_RATE = prefs.getStringInt("sb.fastrate", 60)
		val SB_SLOW_SPEED = prefs.getStringInt("sb.slowspeed", 5)/3.6 // [m/s]
		val SB_SLOW_RATE = prefs.getStringInt("sb.slowrate", 1200)
		if (speed <= SB_SLOW_SPEED)
			SB_SLOW_RATE
		else if (speed >= SB_FAST_SPEED)
			SB_FAST_RATE
		else
			(SB_FAST_RATE + (SB_SLOW_RATE - SB_FAST_RATE) * (SB_FAST_SPEED - speed) / (SB_FAST_SPEED-SB_SLOW_SPEED)).toInt
	}

	// returns the angle between two bearings
	def getBearingAngle(alpha : Float, beta : Float) : Float = {
		val delta = math.abs(alpha-beta)%360
		if (delta <= 180) delta else (360-delta)
	}
	// obtain max speed in [m/s] from moved distance, last and current location
	def getSpeed(location : Location) : Float = {
		val dist = location.distanceTo(lastLoc)
		val t_diff = location.getTime - lastLoc.getTime
		math.max(math.max(dist*1000/t_diff, location.getSpeed), lastLoc.getSpeed)
	}

	def smartBeaconCornerPeg(location : Location) : Boolean = {
		val SB_TURN_TIME = prefs.getStringInt("sb.turntime", 15)
		val SB_TURN_MIN = prefs.getStringInt("sb.turnmin", 10)
		val SB_TURN_SLOPE = prefs.getStringInt("sb.turnslope", 240)*1.0

		val speed = location.getSpeed
		val t_diff = location.getTime - lastLoc.getTime
		val turn = getBearingAngle(location.getBearing, lastLoc.getBearing)

		// no bearing / stillstand -> no corner pegging
		if (!location.hasBearing || speed == 0)
			return false

		// if last bearing unknown, deploy turn_time
		if (!lastLoc.hasBearing)
			return (t_diff/1000 >= SB_TURN_TIME)

		// threshold depends on slope/speed [kmh]
		val threshold = SB_TURN_MIN + SB_TURN_SLOPE/(speed*3.6)

		Log.d(TAG, "smartBeaconCornerPeg: %1.0f < %1.0f %d/%d".format(turn, threshold,
			t_diff/1000, SB_TURN_TIME))
		// need to corner peg if turn time reached and turn > threshold
		(t_diff/1000 >= SB_TURN_TIME && turn > threshold)
	}

	// return true if current position is "new enough" vs. lastLoc
	def smartBeaconCheck(location : Location) : Boolean = {
		if (lastLoc == null)
			return true
		if (smartBeaconCornerPeg(location))
			return true
		val dist = location.distanceTo(lastLoc)
		val t_diff = location.getTime - lastLoc.getTime
		val speed = getSpeed(location)
		//if (location.hasSpeed && location.hasBearing)
		val speed_rate = smartBeaconSpeedRate(speed)
		Log.d(TAG, "smartBeaconCheck: %1.0fm, %1.2fm/s -> %d/%ds - %s".format(dist, speed,
			t_diff/1000, speed_rate, (t_diff/1000 >= speed_rate).toString))
		if (t_diff/1000 >= speed_rate)
			true
		else
			false
	}

	// LocationListener interface
	override def onLocationChanged(location : Location) {
		if (smartBeaconCheck(location))
			postLocation(location)
	}

	override def onProviderDisabled(provider : String) {
		Log.d(TAG, "onProviderDisabled: " + provider)
		if (provider == LocationManager.GPS_PROVIDER) {
			// GPS was our last data source, we have to complain!
			Toast.makeText(service, R.string.service_sm_no_gps, Toast.LENGTH_LONG).show()
		}
	}
	override def onProviderEnabled(provider : String) {
		Log.d(TAG, "onProviderEnabled: " + provider)
	}
	override def onStatusChanged(provider : String, st: Int, extras : Bundle) {
		Log.d(TAG, "onStatusChanged: " + provider)
	}


	def postLocation(location : Location) {
		lastLoc = location

		service.postLocation(location)
	}
}
