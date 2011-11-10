package org.aprsdroid.app


object Backend {
	val PASSCODE_NONE	= 0
	val PASSCODE_OPTIONAL	= 1
	val PASSCODE_REQUIRED	= 2

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
		backend_collection.get(prefs.getString("backend", "")) match {
		case Some(bi) => bi
		case None => backend_collection("tcp")
		}
	}
}

class BackendInfo(
	val create : (AprsService, PrefsWrapper) => AprsIsUploader,
	val prefxml : Int,
	val need_passcode : Int
) {}
