package de.duenndns.aprsdroid

import _root_.android.content.SharedPreferences
import _root_.android.location.Location
import _root_.android.preference.PreferenceManager
import _root_.android.util.Log
import _root_.java.net.{InetAddress, DatagramSocket, DatagramPacket}

class AprsUdp(prefs : SharedPreferences) extends AprsIsUploader(prefs) {
	val TAG = "AprsUdp"
	lazy val socket = new DatagramSocket()

	def start() {
	}

	def update(packet : String) {
		val login = AprsPacket.formatLogin(prefs.getString("callsign", null), prefs.getString("passcode", null))
		var hostname = prefs.getString("host", null)
		val addr = InetAddress.getByName(hostname)
		val pbytes = (login + "\r\n" + packet + "\r\n").getBytes()
		socket.send(new DatagramPacket(pbytes, pbytes.length, addr, 8080))
		Log.d(TAG, "update(): sent " + packet + " to " + hostname)
	}

	def stop() {
	}
}
