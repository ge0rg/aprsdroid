package org.aprsdroid.app

import _root_.android.app.Service
import _root_.android.content.Context
import _root_.android.util.Log
import _root_.android.widget.Toast
import _root_.java.io.{BufferedReader, File, InputStream, InputStreamReader, OutputStream, OutputStreamWriter, PrintWriter}
import _root_.java.net.{InetAddress, Socket}
import _root_.java.security.KeyStore
import _root_.java.security.cert.X509Certificate
import _root_.javax.net.ssl.{KeyManagerFactory, SSLContext, SSLSocket, X509TrustManager}
import _root_.net.ab0oo.aprs.parser.APRSPacket

import scala.collection.JavaConversions._ // for enumeration of keystore aliases

class TcpUploader(service : AprsService, prefs : PrefsWrapper) extends AprsBackend(prefs) {
	val TAG = "APRSdroid.TcpUploader"
	val hostport = prefs.getString("tcp.server", "euro.aprs2.net")
	val so_timeout = prefs.getStringInt("tcp.sotimeout", 120)
	val RECONNECT = 30
	var conn : TcpSocketThread = null

	def start() = {
		if (conn == null)
			createConnection()
		false
	}

	def createConnection() {
		Log.d(TAG, "TcpUploader.createConnection: " + hostport)
		conn = new TcpSocketThread(hostport)
		conn.start()
	}

	def update(packet : APRSPacket) : String = {
		Log.d(TAG, "TcpUploader.update: " + packet)
		conn.update(packet)
	}

	def stop() {
		conn.synchronized {
			conn.running = false
		}
                implicit val ec = scala.concurrent.ExecutionContext.global
		scala.concurrent.Future { conn.shutdown() }
		conn.interrupt()
		conn.join(50)
	}

	class TcpSocketThread(hostport : String)
			extends Thread("APRSdroid TCP connection") {
		val TAG = "APRSdroid.TcpSocketThread"
		var running = true
		var socket : Socket = null
		var tnc : TncProto = null

		val KEYSTORE_DIR = "keystore"
		val KEYSTORE_PASS = "APRS".toCharArray()

		def init_ssl_socket(hostport : String) : Socket = {
			val dir = service.getApplicationContext().getDir(KEYSTORE_DIR, Context.MODE_PRIVATE)
			val keyStoreFile = new File(dir + File.separator + prefs.getCallsign() + ".p12")

			val ks = KeyStore.getInstance("PKCS12")
			val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())

			try {
				ks.load(new java.io.FileInputStream(keyStoreFile), KEYSTORE_PASS)
				for (alias <- ks.aliases()) {
					if (ks.isKeyEntry(alias)) {
						val c = ks.getCertificate(alias).asInstanceOf[X509Certificate]
						// check if the cert is valid, throw up otherwise
						c.checkValidity()
						// work around missing X500Principal.getName(String, Map<String, String) on SDK<9:
						val dn = c.getSubjectX500Principal().toString()
							.replace("OID.1.3.6.1.4.1.12348.1.1=", "CALLSIGN=")
						service.postAddPost(StorageDatabase.Post.TYPE_INFO, R.string.post_info,
							"Loaded key: " + dn)
					}
				}
				kmf.init(ks, KEYSTORE_PASS)
				val sc = SSLContext.getInstance("TLS")
				sc.init(kmf.getKeyManagers(), Array(new NaiveTrustManager()), null)

				val (host, port) = AprsPacket.parseHostPort(hostport, 24580)
				service.postAddPost(StorageDatabase.Post.TYPE_INFO, R.string.post_info,
					service.getString(R.string.post_connecting, host, port.asInstanceOf[AnyRef]))

				val socket = sc.getSocketFactory().createSocket(host, port).asInstanceOf[SSLSocket]
				// enable all available cipher suites, including NULL; fixes #71
				socket.setEnabledCipherSuites(sc.getSocketFactory().getDefaultCipherSuites())
				socket
			} catch {
				case e : java.io.FileNotFoundException =>
					service.postAddPost(StorageDatabase.Post.TYPE_INFO, R.string.post_info,
						service.getString(R.string.ssl_no_keyfile, prefs.getCallsign()))
					return null
				case e : Exception =>
					e.printStackTrace()
					service.postAddPost(StorageDatabase.Post.TYPE_INFO, R.string.post_info, e.toString())
					return null
			}
		}

		def init_socket() {
			Log.d(TAG, "init_socket()")
			this.synchronized {
				if (!running) {
					Log.d(TAG, "init_socket() aborted")
					return;
				}
				socket = init_ssl_socket(hostport)
				if (socket == null) {
					val (host, port) = AprsPacket.parseHostPort(hostport, 14580)
					service.postAddPost(StorageDatabase.Post.TYPE_INFO, R.string.post_info,
						service.getString(R.string.post_connecting, host, port.asInstanceOf[AnyRef]))
					// hack: let the UI thread post a Toast with a passcode warning - only needed in non-SSL mode
					import AprsService.block2runnable
					if (prefs.getPasscode() == "-1") service.handler.post {
						Toast.makeText(service, R.string.anon_warning, Toast.LENGTH_LONG).show()
					}
					socket = new Socket(host, port)
				}
				socket.setKeepAlive(true)
				socket.setSoTimeout(so_timeout*1000)
				tnc = AprsBackend.instanciateProto(service, socket.getInputStream(), socket.getOutputStream())
			}
			Log.d(TAG, "init_socket() done")
		}

		override def run() {
			import StorageDatabase.Post._
			var need_reconnect = false
			Log.d(TAG, "TcpSocketThread.run()")
			try {
				init_socket()
				service.postLinkOn(R.string.p_aprsis_tcp)
				service.postPosterStarted()
			} catch {
				case e : IllegalArgumentException => service.postAbort(e.getMessage()); running = false
				case e : Exception => service.postAbort(e.toString()); running = false
			}
			while (running) {
				try {
					if (need_reconnect) {
						Log.d(TAG, "reconnecting in " + RECONNECT + "s")
						service.postAddPost(TYPE_INFO, R.string.post_info,
							service.getString(R.string.post_reconnect, RECONNECT.asInstanceOf[AnyRef]))
						shutdown()
						Thread.sleep(RECONNECT*1000)
						init_socket()
						need_reconnect = false
						service.postLinkOn(R.string.p_aprsis_tcp)
					}
					Log.d(TAG, "waiting for data...")
					var line : String = null
					while (running && { line = tnc.readPacket(); line != null }) {
						Log.d(TAG, "recv: " + line)
						if (line(0) != '#')
							service.postSubmit(line)
						else
							service.postAddPost(TYPE_INFO, R.string.post_info, line)
					}
					if (running && (line == null || !socket.isConnected()))
						need_reconnect = true
				} catch {
					case se : java.net.SocketTimeoutException =>
						Log.i(TAG, "restarting due to timeout")
						need_reconnect = true
					case e : Exception =>
						Log.d(TAG, "Exception " + e)
						need_reconnect = true
				}
				if (need_reconnect)
					service.postLinkOff(R.string.p_aprsis_tcp)
			}
			Log.d(TAG, "TcpSocketThread.terminate()")
		}

		def update(packet : APRSPacket) : String = {
			if (socket != null && socket.isConnected()) {
				tnc.writePacket(packet)
				"TCP OK"
			} else "TCP disconnected"
		}

		def catchLog(tag : String, fun : ()=>Unit) {
			Log.d(TAG, "catchLog(" + tag + ")")
			try {
				fun()
			} catch {
			case e : Exception => Log.d(TAG, tag + " execption: " + e)
			}
		}

		def shutdown() {
			Log.d(TAG, "shutdown()")
			if (tnc != null)
				tnc.stop()
			this.synchronized {
				catchLog("shutdownInput", socket.shutdownInput)
				catchLog("shutdownOutput", socket.shutdownOutput)
				catchLog("socket.close", socket.close)
				socket = null
			}
		}
	}

	class NaiveTrustManager extends X509TrustManager {

		override def checkClientTrusted(cert: Array[X509Certificate], authType: String) {
		}

		override def checkServerTrusted(cert: Array[X509Certificate], authType: String) {
		}

		override def getAcceptedIssuers = null
	}
}
