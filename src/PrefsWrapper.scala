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
}
