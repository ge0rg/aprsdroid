package org.aprsdroid.app

import _root_.android.content.Context
import _root_.android.location.{Location, LocationManager}
import _root_.android.media.AudioManager
import _root_.android.preference.PreferenceManager

class PrefsWrapper(val context : Context) {
	val prefs = PreferenceManager.getDefaultSharedPreferences(context)
	
	// wrap the "dumb" methods
	def getString(key : String, defValue : String) = prefs.getString(key, defValue)
	def getBoolean(key : String, defValue : Boolean) = prefs.getBoolean(key, defValue)

	// safely read integers
	def getStringInt(key : String, defValue : Int) = {
		try { prefs.getString(key, null).trim.toInt } catch { case _ => defValue }
	}

	// safely read integers
	def getStringFloat(key : String, defValue : Float) = {
		try { prefs.getString(key, null).trim.toFloat } catch { case _ => defValue }
	}

	// return commonly used prefs
	def getCallsign() = prefs.getString("callsign", "").trim().toUpperCase()

	def getPasscode() = prefs.getString("passcode", "") match {
		case "" => "-1"
		case s => s
	}
	def getSsid() = getString("ssid", "10")
	def getCallSsid() = AprsPacket.formatCallSsid(getCallsign(), getSsid())

	def toggleBoolean(name : String, default : Boolean) = {
		val new_val = !prefs.getBoolean(name, default)
		android.util.Log.d("toggleBoolean", name + "=" + new_val)
		prefs.edit().putBoolean(name, new_val).commit()
		new_val
	}
	def getShowObjects() = prefs.getBoolean("show_objects", true)
	def getShowSatellite() = prefs.getBoolean("show_satellite", false)

	def getShowAge() = getStringInt("show_age", 30)*60L*1000
	
	// get the array index for a given list pref
	def getListItemIndex(pref : String, default : String, values : Int) = {
		android.util.Log.d("getLII", getString(pref, default))
		android.util.Log.d("getLII", "values: " + context.getResources().getStringArray(values).mkString(" "))
		context.getResources().getStringArray(values).indexOf(getString(pref, default))
	}

	def getListItemName(pref : String, default : String, values : Int, names : Int) = {
		val id = getListItemIndex(pref, default, values)
		android.util.Log.d("getLIN", "id is " + id)
		if (id < 0)
			"<not in list>"
		else
			context.getResources().getStringArray(names)(id)
	}

	// this is actually a hack!
	def getVersion() = context.getString(R.string.build_version).split(" ").take(2).mkString(" ")

	def getLoginString() = AprsPacket.formatLogin(getCallsign(), getSsid(),
		getPasscode(), getVersion())
	def getFilterString(service : AprsService) : String = {
		val filterdist = getStringInt("tcp.filterdist", 50)
		val userfilter = getString("tcp.filter", "")
		val lastloc = try {
			val locMan = service.getSystemService(Context.LOCATION_SERVICE).asInstanceOf[LocationManager]
			AprsPacket.formatRangeFilter(
				locMan.getLastKnownLocation(PeriodicGPS.bestProvider(locMan)), filterdist)
		} catch {
			case e : IllegalArgumentException => ""
		}
		if (filterdist == 0) return " filter %s %s".format(userfilter, lastloc)
				else return " filter m/%d %s %s".format(filterdist, userfilter, lastloc)
	}

	
	def getAfskHQ() = getBoolean("afsk.hqdemod", true)
	def getAfskBluetooth() = getBoolean("afsk.btsco", false) && getAfskHQ()
	def getAfskOutput() = if (getAfskBluetooth()) AudioManager.STREAM_VOICE_CALL else getStringInt("afsk.output", 0)
}
