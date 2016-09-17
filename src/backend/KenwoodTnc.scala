package org.aprsdroid.app

import _root_.android.util.Log

import _root_.java.io.{InputStream, OutputStream}

class KenwoodTnc(service : AprsService, prefs : PrefsWrapper) extends BluetoothTnc(service, prefs) {
	override val TAG = "APRSdroid.KenwoodTnc"

	override def createTncProto(is : InputStream, os : OutputStream) = {
		new KenwoodProto(service, is, os)
	}

}

