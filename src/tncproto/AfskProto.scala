package org.aprsdroid.app

import _root_.android.util.Log
import _root_.java.io.{InputStream, OutputStream}

import _root_.net.ab0oo.aprs.parser._

class AfskProto(service : AprsService, is : InputStream, os : OutputStream) extends TncProto(is, os) {
	val TAG = "APRSdroid.AfskProto"

	def readPacket() : String = {
		""
	}

	def writePacket(p : APRSPacket) {
	}
}
