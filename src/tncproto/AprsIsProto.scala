package org.aprsdroid.app
import _root_.java.io.{BufferedReader, InputStream, InputStreamReader, OutputStream, OutputStreamWriter, PrintWriter}

import _root_.net.ab0oo.aprs.parser._

class AprsIsProto(service : AprsService, is : InputStream, os : OutputStream) extends Tnc2Proto(is, os) {
	val loginfilter = service.prefs.getLoginString() + service.prefs.getFilterString(service)

	service.postAddPost(StorageDatabase.Post.TYPE_TX,
		R.string.p_conn_aprsis, loginfilter)
	writer.println(loginfilter)
}
