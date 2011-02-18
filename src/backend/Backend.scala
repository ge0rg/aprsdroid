package de.duenndns.aprsdroid

import _root_.android.content.SharedPreferences

object Backend {
	val backend_collection = Map(
		"udp" -> new BackendInfo(
			(s, p) => new UdpUploader(p),
			R.xml.pref_udp,
			true),
		"http" -> new BackendInfo(
			(s, p) => new HttpPostUploader(p),
			R.xml.pref_http,
			true),
		"afsk" -> new BackendInfo(
			(s, p) => new AfskUploader(p),
			R.xml.pref_afsk,
			false),
		"tcp" -> new BackendInfo(
			(s, p) => new TcpUploader(s, p),
			R.xml.pref_tcp,
			true)
		)

	def defaultBackendInfo(prefs : SharedPreferences) : BackendInfo = {
		backend_collection.get(prefs.getString("conntype", "")) match {
		case Some(bi) => bi
		case None => backend_collection("tcp")
		}
	}
}

class BackendInfo(
	val create : (AprsService, SharedPreferences) => AprsIsUploader,
	val prefxml : Int,
	val need_passcode : Boolean
) {}
