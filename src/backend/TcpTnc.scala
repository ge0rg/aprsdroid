package org.aprsdroid.app

import _root_.java.io.{InputStream, OutputStream}

class TcpTnc(service : AprsService, prefs : PrefsWrapper) extends TcpUploader(service, prefs) {
	override val TAG = "APRSdroid.TcpTnc"
	val digipath = prefs.getString("digi_path", "WIDE1-1")

	override def createTncProto(is : InputStream, os : OutputStream) : TncProto =
		new KissProto(is, os, digipath)

}
