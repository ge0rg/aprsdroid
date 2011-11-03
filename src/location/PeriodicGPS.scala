package org.aprsdroid.app

import _root_.android.content.Context
import _root_.android.location._
import _root_.android.os.{Bundle, Handler}
import _root_.android.util.Log
import _root_.android.widget.Toast

class PeriodicGPS(service : AprsService, prefs : PrefsWrapper) extends LocationListener {
	val TAG = "APRSdroid.PeriodicGPS"

	val FAST_LANE_ACT = 30000

	lazy val locMan = service.getSystemService(Context.LOCATION_SERVICE).asInstanceOf[LocationManager]

	var lastLoc : Location = null
	var fastLaneLoc : Location = null

	def start(singleShot : Boolean) {
		requestLocations(singleShot)
	}

	def restart() {
		fastLaneLoc = null
		lastLoc = null
	}

	def requestLocations(stay_on : Boolean) {
		// get update interval and distance
		val upd_int = prefs.getStringInt("interval", 10)
		val upd_dist = prefs.getStringInt("distance", 10)
		val gps_act = prefs.getString("gps_activation", "med")
		if (stay_on || (gps_act == "always")) {
			locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER,
				0, 0, this)
		} else {
			// for GPS precision == medium, we use getGpsInterval()
			locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER,
				upd_int * 60000 - getGpsInterval(), upd_dist * 1000, this)
		}
		if (prefs.getBoolean("netloc", false)) {
			locMan.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
				upd_int * 60000, upd_dist * 1000, this)
		}
	}


	def stop() {
		locMan.removeUpdates(this);
	}

	def getGpsInterval() : Int = {
		val gps_act = prefs.getString("gps_activation", "med")
		if (gps_act == "med") FAST_LANE_ACT
				else  0
	}

	def startFastLane() {
		import AprsService.block2runnable
		Log.d(TAG, "switching to fast lane");
		// request fast update rate
		locMan.removeUpdates(this);
		requestLocations(true)
		service.handler.postDelayed({ stopFastLane(true) }, FAST_LANE_ACT)
	}

	def stopFastLane(post : Boolean) {
		if (!AprsService.running)
			return;
		Log.d(TAG, "switching to slow lane");
		if (post && fastLaneLoc != null) {
			Log.d(TAG, "stopFastLane: posting " + fastLaneLoc);
			postLocation(fastLaneLoc)
		}
		fastLaneLoc = null
		// reset update speed
		locMan.removeUpdates(this);
		requestLocations(false)
	}

	def goingFastLane(location : Location) : Boolean = {
		if (fastLaneLoc == null) {
			// need to set fastLaneLoc before re-requesting locations
			fastLaneLoc = location
			startFastLane()
		} else
			fastLaneLoc = location
		return true
	}

	def smartBeaconSpeedRate(speed : Float) : Int = {
		val SB_FAST_SPEED = 28 // [m/s] = ~100km/h
		val SB_FAST_RATE = 60
		val SB_SLOW_SPEED = 1 // [m/s] = 3.6km/h
		val SB_SLOW_RATE = 1200
		if (speed <= SB_SLOW_SPEED)
			SB_SLOW_RATE
		else if (speed >= SB_FAST_SPEED)
			SB_FAST_RATE
		else
			((SB_SLOW_RATE - SB_FAST_RATE) * (SB_FAST_SPEED - speed) / (SB_FAST_SPEED-SB_SLOW_SPEED)).toInt
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
		val SB_TURN_TIME = 15
		val SB_TURN_MIN = 10
		val SB_TURN_SLOPE = 240.0

		val speed = getSpeed(location)
		val t_diff = location.getTime - lastLoc.getTime
		val turn = getBearingAngle(location.getBearing, lastLoc.getBearing)

		// no bearing / stillstand -> no corner pegging
		if (!location.hasBearing || speed == 0)
			return false

		// if last bearing unknown, deploy turn_time
		if (!lastLoc.hasBearing)
			return (t_diff/1000 >= SB_TURN_TIME)

		// threshold depends on slope/speed [mph]
		val threshold = SB_TURN_MIN + SB_TURN_SLOPE/(speed*2.23693629)

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
		val upd_int = prefs.getStringInt("interval", 10) * 60000
		val upd_dist = prefs.getStringInt("distance", 10) * 1000
		if (prefs.getBoolean("smartbeaconing", true)) {
			if (!smartBeaconCheck(location))
				return
		} else /* no smartbeaconing */
		if (lastLoc != null &&
		    (location.getTime - lastLoc.getTime < (upd_int  - getGpsInterval()) ||
		     location.distanceTo(lastLoc) < upd_dist)) {
			//Log.d(TAG, "onLocationChanged: ignoring premature location")
			return
		}
		// check if we need to go fast lane
		val gps_act = prefs.getString("gps_activation", "med")
		if (gps_act == "med" && location.getProvider == LocationManager.GPS_PROVIDER) {
			if (goingFastLane(location))
				return
		}
		postLocation(location)
	}

	override def onProviderDisabled(provider : String) {
		Log.d(TAG, "onProviderDisabled: " + provider)
		val netloc_available = locMan.getProviders(true).contains(LocationManager.NETWORK_PROVIDER)
		val netloc_usable = netloc_available && prefs.getBoolean("netloc", false)
		if (provider == LocationManager.GPS_PROVIDER &&
			netloc_usable == false) {
			// GPS was our last data source, we have to complain!
			Toast.makeText(service, R.string.service_no_location, Toast.LENGTH_LONG).show()
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
