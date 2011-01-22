package de.duenndns.aprsdroid

import _root_.android.content.SharedPreferences
import _root_.android.location.Location
import _root_.android.util.Log
import _root_.org.apache.http._
import _root_.org.apache.http.entity.StringEntity
import _root_.org.apache.http.impl.client.DefaultHttpClient
import _root_.org.apache.http.client.methods.HttpPost

class HttpPostUploader(prefs : SharedPreferences) extends AprsIsUploader(prefs) {
	val TAG = "AprsHttpPost"
	val host = prefs.getString("http.server", "srvr.aprs-is.net")

	def start() {
	}

	def doPost(urlString : String, content : String) : String = {
		val client = new DefaultHttpClient()
		val post = new HttpPost(urlString)
		post.setEntity(new StringEntity(content))
		post.addHeader("Content-Type", "application/octet-stream");
		post.addHeader("Accept-Type", "text/plain");
		val response = client.execute(post)
		Log.d(TAG, "doPost(): " + response.getStatusLine())
		"HTTP " + response.getStatusLine().getReasonPhrase()
	}

	def update(packet : String) : String = {
		var hostname = host
		if (hostname.indexOf(":") == -1) {
			hostname = "http://" + hostname + ":8080/"
		}
		doPost(hostname, login + "\r\n" + packet + "\r\n")
	}

	def stop() {
	}
}
