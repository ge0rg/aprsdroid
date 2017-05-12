package org.aprsdroid.app
import _root_.java.io.{BufferedReader, InputStream, InputStreamReader, OutputStream, OutputStreamWriter, PrintWriter}

import _root_.net.ab0oo.aprs.parser._

class Tnc2Proto(is : InputStream, os : OutputStream) extends TncProto(is, os) {
	val reader = new BufferedReader(new InputStreamReader(is), 256)
	val writer = new PrintWriter(new OutputStreamWriter(os), true)

	def readPacket() : String = reader.readLine()
	def writePacket(p : APRSPacket) = writer.println(p)
}
