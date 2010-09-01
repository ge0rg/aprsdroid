package de.duenndns.aprsdroid

import _root_.android.location.Location
import _root_.android.preference.PreferenceManager
import _root_.android.util.Log
import _root_.java.net.{InetAddress, DatagramSocket, DatagramPacket}
import com.nogy.afu.soundmodem.{Message, APRSFrame, Afsk}

class AfskUploader(host : String, login : String) extends AprsIsUploader(host, login) {
	val TAG = "AprsAfsk"
	var FrameLength = 150	//1200Bits = 1sec to open VOX
	var Digis = "WIDE1-1"
	
	def start() {
	}

	def update(packet : String) : String = {
		// Need to "parse" the packet in order to replace the Digipeaters
		var from = packet.split('>')
		var temp : String = ""
		var i=1
		while (i<from.length-1)
		{
			temp += from(i)+">"
			i=i+1
		}
		temp+=from(i)
		var to = temp.split(',')
		i=1
		temp = ""
		while (i<to.length-1)
		{
			temp += to(i)+","
			i=i+1
		}
		temp+=to(i)
		var digi = temp.split(':')
		i=1
		temp = ""
		while (i<digi.length-1)
		{
			temp += digi(i)+":"
			i=i+1
		}
		temp+=digi(i)
		var data = temp
		var msg : Message = (new APRSFrame(from(0),to(0),Digis,data,FrameLength)).getMessage()
		var mod : Afsk = new Afsk()
		mod.sendMessage(msg)
		Log.d(TAG, "update(): From: " + from +" To: "+ to +" Via: " + digi + " telling " + data)
		"AFSK OK"
	}

	def stop() {
	}

	// Non Interface methods
	def set_FrameLength(length: Int) = {
		FrameLength = length
	}

	def set_Digis(Digipeaters : String) = {
		Digis = Digipeaters
	}
}
