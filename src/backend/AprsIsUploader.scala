package org.aprsdroid.app

import _root_.net.ab0oo.aprs.parser.APRSPacket

object AprsIsUploader {
	val DEFAULT_CONNTYPE = "tcp"

	val PASSCODE_NONE	= 0
	val PASSCODE_OPTIONAL	= 1
	val PASSCODE_REQUIRED	= 2

	// "struct" for APRS backend information
	class BackendInfo(
		val create : (AprsService, PrefsWrapper) => AprsIsUploader,
		val prefxml : Int,
		val need_passcode : Int
	) {}

	// add your own BackendInfo here
	val backend_collection = Map(
		"udp" -> new BackendInfo(
			(s, p) => new UdpUploader(p),
			R.xml.backend_udp,
			PASSCODE_REQUIRED),
		"http" -> new BackendInfo(
			(s, p) => new HttpPostUploader(p),
			R.xml.backend_http,
			PASSCODE_REQUIRED),
		"afsk" -> new BackendInfo(
			(s, p) => new AfskUploader(p),
			R.xml.backend_afsk,
			PASSCODE_NONE),
		"tcp" -> new BackendInfo(
			(s, p) => new TcpUploader(s, p),
			R.xml.backend_tcp,
			PASSCODE_OPTIONAL),
		"bluetooth" -> new BackendInfo(
			(s, p) => new BluetoothTnc(s, p),
			R.xml.backend_bluetooth,
			PASSCODE_NONE)
		)

	def defaultBackendInfo(prefs : PrefsWrapper) : BackendInfo = {
		backend_collection.get(prefs.getString("backend", DEFAULT_CONNTYPE)) match {
		case Some(bi) => bi
		case None => backend_collection(DEFAULT_CONNTYPE)
		}
	}

	def instanciateUploader(service : AprsService, prefs : PrefsWrapper) : AprsIsUploader = {
		defaultBackendInfo(prefs).create(service, prefs)
	}
	def instanciatePrefsAct(prefs : PrefsWrapper) = {
		defaultBackendInfo(prefs).prefxml
	}

}

abstract class AprsIsUploader(prefs : PrefsWrapper) {
	val login = prefs.getLoginString()

	// returns true if successfully started.
	// when returning false, AprsService.postPosterStarted() must be called
	def start() : Boolean

	def update(packet : APRSPacket) : String

	def stop()
}
