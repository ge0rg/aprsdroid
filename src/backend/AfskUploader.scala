package org.aprsdroid.app

import _root_.android.util.Log
import _root_.java.net.{InetAddress, DatagramSocket, DatagramPacket}
import com.nogy.afu.soundmodem.{Message, APRSFrame, Afsk}

class AfskUploader(prefs : PrefsWrapper) extends AprsIsUploader(prefs) {
	val TAG = "AprsAfsk"
	// frame prefix: bytes = milliseconds * baudrate / 8 / 1000
	var FrameLength = prefs.getStringInt("afsk.prefix", 1000)*1200/8/1000
	var Digis = prefs.getString("digi_path", "WIDE1-1")
	
	def start() {
	}

	def update(packet : String) : String = {
		// Need to "parse" the packet in order to replace the Digipeaters
		val Array(from, to_data) = packet.split(">", 2)
		val Array(to_digis, data) = to_data.split(":", 2)
		val Array(to, digis) = to_digis.split(",", 2)
		val msg = new APRSFrame(from,to,Digis,data,FrameLength).getMessage()
		val mod = new Afsk()
		mod.sendMessage(msg)
		Log.d(TAG, "update(): From: " + from +" To: "+ to +" Via: " + Digis + " telling " + data)
		"AFSK OK"
	}

	def stop() {
	}

}
