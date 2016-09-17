package org.aprsdroid.app
import _root_.java.io.{InputStream, OutputStream}

import _root_.net.ab0oo.aprs.parser._

abstract class TncProto(is : InputStream, os : OutputStream) {
	def readPacket() : String
	def writePacket(p : APRSPacket)
	def stop() {}
}
