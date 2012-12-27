package org.aprsdroid.app

import _root_.net.ab0oo.aprs.parser.APRSPacket

object AprsIsUploader {
	val DEFAULT_CONNTYPE = "tcp"

	val PASSCODE_NONE	= 0
	val PASSCODE_OPTIONAL	= 1
	val PASSCODE_REQUIRED	= 2

	val CAN_RECEIVE		= 1
	val CAN_XMIT		= 2
	val CAN_DUPLEX		= 3

	// "struct" for APRS backend information
	class BackendInfo(
		val create : (AprsService, PrefsWrapper) => AprsIsUploader,
		val prefxml : Int,
		val duplex : Int,
		val need_passcode : Int
	) {}

	// add your own BackendInfo here
	val backend_collection = Map(
		"udp" -> new BackendInfo(
			(s, p) => new UdpUploader(p),
			R.xml.backend_udp,
			CAN_XMIT,
			PASSCODE_REQUIRED),
		"http" -> new BackendInfo(
			(s, p) => new HttpPostUploader(p),
			R.xml.backend_http,
			CAN_XMIT,
			PASSCODE_REQUIRED),
		"afsk" -> new BackendInfo(
			(s, p) => new AfskUploader(s, p),
			R.xml.backend_afsk,
			CAN_DUPLEX,
			PASSCODE_NONE),
		"tcp" -> new BackendInfo(
			(s, p) => new TcpUploader(s, p),
			R.xml.backend_tcp,
			CAN_DUPLEX,
			PASSCODE_OPTIONAL),
		"bluetooth" -> new BackendInfo(
			(s, p) => new BluetoothTnc(s, p),
			R.xml.backend_bluetooth,
			CAN_DUPLEX,
			PASSCODE_NONE),
		"kenwood" -> new BackendInfo(
			(s, p) => new KenwoodTnc(s, p),
			R.xml.backend_kenwood,
			CAN_RECEIVE,
			PASSCODE_NONE),
		"tcptnc" -> new BackendInfo(
			(s, p) => new TcpTnc(s, p),
			R.xml.backend_tcptnc,
			CAN_DUPLEX,
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
