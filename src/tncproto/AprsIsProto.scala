package org.aprsdroid.app
import _root_.java.io.{BufferedReader, InputStream, InputStreamReader, OutputStream, OutputStreamWriter, PrintWriter}

import _root_.net.ab0oo.aprs.parser._

class AprsIsProto(service : AprsService, is : InputStream, os : OutputStream) extends TncProto(is, os) {
	val loginfilter = service.prefs.getLoginString() + service.prefs.getFilterString(service)

	val reader = new BufferedReader(new InputStreamReader(is), 256)
	val writer = new PrintWriter(new OutputStreamWriter(os), true)

	writer.println(loginfilter)

	def readPacket() : String = reader.readLine()
	def writePacket(p : APRSPacket) = writer.println(p)
}
