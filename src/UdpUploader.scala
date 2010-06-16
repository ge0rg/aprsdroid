package de.duenndns.aprsdroid

import _root_.android.location.Location
import _root_.android.preference.PreferenceManager
import _root_.android.util.Log
import _root_.java.net.{InetAddress, DatagramSocket, DatagramPacket}

class UdpUploader(host : String, login : String) extends AprsIsUploader(host, login) {
	val TAG = "AprsUdp"
	lazy val socket = new DatagramSocket()

	def start() {
	}

	def update(packet : String) : String = {
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
