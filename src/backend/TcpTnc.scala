package org.aprsdroid.app

import _root_.java.io.{InputStream, OutputStream}

class TcpTnc(service : AprsService, prefs : PrefsWrapper) extends TcpUploader(service, prefs) {
	override val TAG = "APRSdroid.TcpTnc"

	override def createTncProto(is : InputStream, os : OutputStream) : TncProto =
		AprsBackend.instanciateProto("kiss", service, is, os)

}
