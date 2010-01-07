package de.duenndns.aprsdroid

import _root_.android.content.SharedPreferences
import _root_.android.location.Location
import _root_.android.preference.PreferenceManager
import _root_.android.util.Log
import _root_.java.io.InputStream
import _root_.java.net.{URL, HttpURLConnection}

class AprsHttpPost(prefs : SharedPreferences) extends AprsIsUploader(prefs) {
	val TAG = "AprsHttpPost"

	def start() {
	}

	def doPost(urlString : String, content : String) {
		val url = new URL(urlString)
		val con = url.openConnection().asInstanceOf[HttpURLConnection]
		con.setRequestMethod("POST")
		con.setRequestProperty("Content-Type", "application/octet-stream");
		con.setRequestProperty("Accept-Type", "text/plain");
		con.setRequestProperty("Connection", "close");
		con.setDoOutput(true)
		con.setConnectTimeout(30000)
		con.setReadTimeout(30000)
		//con.setDoInput(true)
		con.connect()
		val out = con.getOutputStream()
		val buff = content.getBytes("UTF8")
		out.write(buff)
		out.flush()
		out.close()

		Log.d(TAG, "doPost(): " + con.getResponseCode() + " - " + con.getResponseMessage())
		con.disconnect()
		//return con.getInputStream()
	} 

	def update(packet : String) {
		val login = "user " + prefs.getString("callsign", null) +
			" pass " + prefs.getString("passcode", null) + " vers APRSdroid beta"
		var hostname = prefs.getString("host", null)
		if (hostname.indexOf(":") == -1) {
			hostname = "http://" + hostname + ":8080/"
		}
		doPost(hostname, login + "\r\n" + packet + "\r\n")
	}

	def stop() {
	}
}
