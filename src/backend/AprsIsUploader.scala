package de.duenndns.aprsdroid

import _root_.android.content.SharedPreferences

object AprsIsUploader {
	val DEFAULT_CONNTYPE = "tcp"

	def instanciateUploader(service : AprsService, prefs : SharedPreferences) : AprsIsUploader = {
		prefs.getString("conntype", DEFAULT_CONNTYPE) match {
		case "udp" =>
			new UdpUploader(prefs)
		case "http" =>
			new HttpPostUploader(prefs)
		case "afsk" =>
			new AfskUploader(prefs)
		case _ =>
			new TcpUploader(service, prefs)
		}
	}
	def instanciatePrefsAct(prefs : SharedPreferences) = {
		prefs.getString("conntype", DEFAULT_CONNTYPE) match {
		case "afsk" => R.xml.pref_afsk
		case "udp" => R.xml.pref_udp
		case _ => R.xml.pref_tcp // TCP is default
		}
	}
}

abstract class AprsIsUploader(prefs : SharedPreferences) {
	val login = AprsPacket.formatLogin(prefs.getString("callsign", null).trim(),
		prefs.getString("ssid", null), prefs.getString("passcode", null))

	def start()

	def update(packet : String) : String

	def stop()
}
