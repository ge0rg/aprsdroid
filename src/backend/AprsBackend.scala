
package org.aprsdroid.app

import android.Manifest
import android.os.Build
import _root_.android.util.Log
import _root_.net.ab0oo.aprs.parser.APRSPacket
import _root_.java.io.{InputStream, OutputStream}

object AprsBackend {
	val TAG = "AprsBackend"
        /** "Modular" system to connect to an APRS backend.
        * The backend config consists of three items backed by prefs values:
        *   - *proto* inside the connection ("aprsis", "afsk", "kiss", "tnc2", "kenwood") - ProtoInfo class
        *   - *link* type ("bluetooth", "usb", "tcpip"; only for "kiss", "tnc2", "kenwood") - BackendInfo class
        *   - *aprsis* mode ("tcp", "http", "udp"; only for proto=aprsis) - BackendInfo class
        */
	val DEFAULT_CONNTYPE = "tcp"
	val DEFAULT_LINK = "tcpip"
	val DEFAULT_PROTO = "aprsis"

	val PASSCODE_NONE	= 0
	val PASSCODE_OPTIONAL	= 1
	val PASSCODE_REQUIRED	= 2

	val CAN_RECEIVE		= 1
	val CAN_XMIT		= 2
	val CAN_DUPLEX		= 3

	// "struct" for APRS backend information
	class BackendInfo(
		val create : (AprsService, PrefsWrapper) => AprsBackend,
		val prefxml : Int,
		val permissions : Set[String],
		val duplex : Int,
		val need_passcode : Int
	) {}

	val BLUETOOTH_PERMISSION = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
		Manifest.permission.BLUETOOTH_CONNECT
	} else {
		Manifest.permission.BLUETOOTH_ADMIN
	}

	// map from old "backend" to new proto-link-aprsis (defaults are bluetooth and tcp)
	val backend_upgrade = Map(
		"tcp" -> "aprsis-tcpip-tcp",
		"udp" -> "aprsis-tcpip-udp",
		"http" -> "aprsis-tcpip-http",
		"afsk" -> "afsk-bluetooth-tcp",
		"bluetooth" -> "kiss-bluetooth-tcp",
		"kenwood" -> "kenwood-bluetooth-tcp",
		"tcptnc" -> "kiss-tcpip-tcp",
		"usb" -> "kiss-usb-tcp"
		)

	// add your own BackendInfo here
	val backend_collection = Map(
		"udp" -> new BackendInfo(
			(s, p) => new UdpUploader(p),
			R.xml.backend_udp,
			Set(),
			CAN_XMIT,
			PASSCODE_REQUIRED),
		"http" -> new BackendInfo(
			(s, p) => new HttpPostUploader(p),
			R.xml.backend_http,
			Set(),
			CAN_XMIT,
			PASSCODE_REQUIRED),
		"vox" -> new BackendInfo(
			(s, p) => new AfskUploader(s, p),
			0,
			Set(Manifest.permission.RECORD_AUDIO),
			CAN_DUPLEX,
			PASSCODE_NONE),
		"tcp" -> new BackendInfo(
			(s, p) => new TcpUploader(s, p),
			R.xml.backend_tcp,
			Set(),
			CAN_DUPLEX,
			PASSCODE_OPTIONAL),
		"bluetooth" -> new BackendInfo(
			(s, p) => new BluetoothTnc(s, p),
			R.xml.backend_bluetooth,
			Set(BLUETOOTH_PERMISSION),
			CAN_DUPLEX,
			PASSCODE_NONE),
		"ble" -> new BackendInfo(
			(s, p) => new BluetoothLETnc(s, p),
			R.xml.backend_ble,
			Set(BLUETOOTH_PERMISSION),
			CAN_DUPLEX,
			PASSCODE_NONE),			
		"tcpip" -> new BackendInfo(
			(s, p) => new TcpUploader(s, p),
			R.xml.backend_tcptnc,
			Set(),
			CAN_DUPLEX,
			PASSCODE_NONE),
		"usb" -> new BackendInfo(
			(s, p) => new UsbTnc(s, p),
			R.xml.backend_usb,
			Set(),
			CAN_DUPLEX,
			PASSCODE_NONE),
		"digirig" -> new BackendInfo(
			(s, p) => new DigiRig(s, p),
			R.xml.backend_digirig,
                        Set(Manifest.permission.RECORD_AUDIO),
			CAN_DUPLEX,
			PASSCODE_NONE
		)
		)

	class ProtoInfo(
		val create : (AprsService, InputStream, OutputStream) => TncProto,
		val prefxml : Int,
		val link : String
	) {}

	val proto_collection = Map(
		"aprsis" -> new ProtoInfo(
			(s, is, os) => new AprsIsProto(s, is, os),
			R.xml.proto_aprsis, "aprsis"),
		"afsk" -> new ProtoInfo(
			(s, is, os) => new AfskProto(s, is, os),
			R.xml.proto_afsk, "afsk"),
		"kiss" -> new ProtoInfo(
			(s, is, os) => new KissProto(s, is, os),
			R.xml.proto_kiss, "link"),
		"tnc2" -> new ProtoInfo(
			(s, is, os) => new Tnc2Proto(is, os),
			R.xml.proto_tnc2, "link"),
		"kenwood" -> new ProtoInfo(
			(s, is, os) => new KenwoodProto(s, is, os),
			R.xml.proto_kenwood, "link")
	);
	def defaultProtoInfo(p : String) : ProtoInfo = {
		proto_collection.get(p) match {
		case Some(pi) => pi
		case None => proto_collection("aprsis")
		}
	}
	def defaultProtoInfo(prefs : PrefsWrapper) : ProtoInfo = defaultProtoInfo(prefs.getProto())

	def defaultBackendInfo(prefs : PrefsWrapper) : BackendInfo = {
		val pi = defaultProtoInfo(prefs)
		var link = ""
		if (pi.link != null) {
			link = prefs.getString(pi.link, DEFAULT_LINK)
			Log.d(TAG, "DEBUG: pi.link (" + pi.link + ") != null : " + link)
		} else {
			link = prefs.getProto()
			Log.d(TAG, "DEBUG: pi.link == null : " + link)
		}

		backend_collection.get(link) match {
		case Some(bi) => bi
		case None => backend_collection(DEFAULT_CONNTYPE)
		}
	}

	def defaultBackendPermissions(prefs : PrefsWrapper) : Set[String] = {
		val perms = scala.collection.mutable.Set[String]()
		perms ++= AprsBackend.defaultBackendInfo(prefs).permissions
		if (prefs.getProto() == "kenwood" && prefs.getBoolean("kenwood.gps", false))
			perms += (Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        perms += (Manifest.permission.POST_NOTIFICATIONS)
                }
		perms.toSet
	}

	def instanciateUploader(service : AprsService, prefs : PrefsWrapper) : AprsBackend = {
		defaultBackendInfo(prefs).create(service, prefs)
	}
	def instanciateProto(service : AprsService, is : InputStream, os : OutputStream) : TncProto = {
		defaultProtoInfo(service.prefs).create(service, is, os)
	}
	def prefxml_proto(prefs : PrefsWrapper) = {
		defaultProtoInfo(prefs).prefxml
	}
	def prefxml_backend(prefs : PrefsWrapper) = {
		defaultBackendInfo(prefs).prefxml
	}

}

abstract class AprsBackend(prefs : PrefsWrapper) {
	val login = prefs.getLoginString()

	// returns true if successfully started.
	// when returning false, AprsService.postPosterStarted() must be called
	def start() : Boolean

	def update(packet : APRSPacket) : String

	def stop()
}
