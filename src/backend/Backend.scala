package org.aprsdroid.app

import _root_.android.content.SharedPreferences

object Backend {
	val PASSCODE_NONE	= 0
	val PASSCODE_OPTIONAL	= 1
	val PASSCODE_REQUIRED	= 2

	val backend_collection = Map(
		"udp" -> new BackendInfo(
			(s, p) => new UdpUploader(p),
			R.xml.pref_udp,
			PASSCODE_REQUIRED),
		"http" -> new BackendInfo(
			(s, p) => new HttpPostUploader(p),
			R.xml.pref_http,
			PASSCODE_REQUIRED),
		"afsk" -> new BackendInfo(
			(s, p) => new AfskUploader(p),
			R.xml.pref_afsk,
			PASSCODE_NONE),
		"tcp" -> new BackendInfo(
			(s, p) => new TcpUploader(s, p),
			R.xml.pref_tcp,
			PASSCODE_OPTIONAL)
		)

	def defaultBackendInfo(prefs : SharedPreferences) : BackendInfo = {
		backend_collection.get(prefs.getString("backend", "")) match {
		case Some(bi) => bi
		case None => backend_collection("tcp")
		}
	}
}

class BackendInfo(
	val create : (AprsService, SharedPreferences) => AprsIsUploader,
	val prefxml : Int,
	val need_passcode : Int
) {}
