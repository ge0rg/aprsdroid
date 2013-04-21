package org.aprsdroid.app

import _root_.android.location.Location
import _root_.android.util.Log
import _root_.java.net.{InetAddress, DatagramSocket, DatagramPacket}
import _root_.net.ab0oo.aprs.parser.APRSPacket

class UdpUploader(prefs : PrefsWrapper) extends AprsBackend(prefs) {
	val TAG = "APRSdroid.Udp"
	lazy val socket = new DatagramSocket()
	val host = prefs.getString("udp.server", "srvr.aprs-is.net")

	def start() = true

	def update(packet : APRSPacket) : String = {
		val (h, port) = AprsPacket.parseHostPort(host, 8080)
		val addr = InetAddress.getByName(h)
		val pbytes = (login + "\r\n" + packet + "\r\n").getBytes()
		socket.send(new DatagramPacket(pbytes, pbytes.length, addr, port))
		Log.d(TAG, "update(): sent '" + packet + "' to " + host)
		"UDP OK"
	}

	def stop() {
	}
}
