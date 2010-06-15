package de.duenndns.aprsdroid

import _root_.android.location.Location
import _root_.android.preference.PreferenceManager
import _root_.android.util.Log
import _root_.java.net.{InetAddress, DatagramSocket, DatagramPacket}

class UdpUploader extends AprsIsUploader {
	val TAG = "AprsUdp"
	lazy val socket = new DatagramSocket()

	def start() {
	}

	def update(host : String, login : String, packet : String) : String = {
		val addr = InetAddress.getByName(host)
		val pbytes = (login + "\r\n" + packet + "\r\n").getBytes()
		socket.send(new DatagramPacket(pbytes, pbytes.length, addr, 8080))
		Log.d(TAG, "update(): sent '" + packet + "' to " + host)
		"UDP OK"
	}

	def stop() {
	}
}
