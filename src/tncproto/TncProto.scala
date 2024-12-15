package org.aprsdroid.app
import _root_.java.io.{InputStream, OutputStream}

import _root_.net.ab0oo.aprs.parser._

object KissConstants {
  val FEND  = 0xC0
  val CONTROL_COMMAND = 0x06
  val RETURN = 0xEB
}


abstract class TncProto(is : InputStream, os : OutputStream) {
  import KissConstants._  // Import the constants from the shared object

	def readPacket() : String
	def writePacket(p : APRSPacket)
	def writeReturn(): Unit = {
		val frame = Array[Byte](
		FEND.toByte,     // Start frame
		CONTROL_COMMAND.toByte,  // Command byte
		RETURN.toByte,      // Return command byte
		FEND.toByte)      // End frame
		os.write(frame)
		os.flush()
	  }
	def stop() {}
}
