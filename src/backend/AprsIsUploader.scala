package org.aprsdroid.app


object AprsIsUploader {
	val DEFAULT_CONNTYPE = "tcp"

	def instanciateUploader(service : AprsService, prefs : PrefsWrapper) : AprsIsUploader = {
		Backend.defaultBackendInfo(prefs).create(service, prefs)
	}
	def instanciatePrefsAct(prefs : PrefsWrapper) = {
		Backend.defaultBackendInfo(prefs).prefxml
	}
}

abstract class AprsIsUploader(prefs : PrefsWrapper) {
	val login = AprsPacket.formatLogin(prefs.getCallsign(),
		prefs.getString("ssid", null), prefs.getPasscode())

	def start()

	def update(packet : String) : String

	def stop()
}
