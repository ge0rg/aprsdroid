package org.aprsdroid.app

import _root_.android.content.Context
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

	// return commonly used prefs
	def getCallsign() = prefs.getString("callsign", "").trim()

	def getPasscode() = prefs.getString("passcode", "") match {
		case "" => "-1"
		case s => s
	}
	def getCallSsid() = AprsPacket.formatCallSsid(getCallsign(), getString("ssid", ""))

	def toggleBoolean(name : String, default : Boolean) = {
		val new_val = !prefs.getBoolean(name, default)
		android.util.Log.d("toggleBoolean", name + "=" + new_val)
		prefs.edit().putBoolean(name, new_val).commit()
		new_val
	}
	def getShowObjects() = prefs.getBoolean("show_objects", false)
	def getShowSatellite() = prefs.getBoolean("show_satellite", false)

	def getShowAge() = getStringInt("show_age", 30)*60L*1000
	
	// this is actually a hack!
	def getVersion() = context.getString(R.string.build_version).split(" ").take(2).mkString(" ")

	def getLoginString() = AprsPacket.formatLogin(getCallsign(), getString("ssid", null),
		getPasscode(), getVersion())
}
