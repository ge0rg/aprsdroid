package de.duenndns.aprsdroid

import _root_.android.content.SharedPreferences
import _root_.android.app.Service
import _root_.android.location.{Location, LocationManager}
import _root_.android.preference.PreferenceManager
import _root_.android.util.Log
import _root_.java.io.{BufferedReader, InputStreamReader, OutputStreamWriter, PrintWriter}
import _root_.java.net.{InetAddress, Socket}

class TcpUploader(service : AprsService, prefs : SharedPreferences) extends AprsIsUploader(prefs) {
	val TAG = "TcpUploader"
	val hostname = prefs.getString("tcp.server", "srvr.aprs-is.net")
	val filterdist = prefs.getString("tcp.filter", "m/10")
	val lastloc = AprsPacket.formatRangeFilter(
		service.locMan.getLastKnownLocation(LocationManager.GPS_PROVIDER), 10)
	val filter = " filter %s %s".format(filterdist, lastloc)
	var conn : TcpSocketThread = null

	createConnection()

	def start() {
	}

	def createConnection() {
		val (host, port) = AprsPacket.parseHostPort(hostname, 14580)
		Log.d(TAG, "TcpUploader.createConnection: " + host + ":" + port)
		conn = new TcpSocketThread(host, port)
		conn.start()
	}

	def update(packet : String) : String = {
		Log.d(TAG, "TcpUploader.update: " + packet)
		conn.update(packet)
	}

	def stop() {
		conn.shutdown()
		conn.interrupt()
		conn.join()
	}

	class TcpSocketThread(host : String, port : Int)
			extends Thread("APRSdroid TCP connection") {
		val TAG = "TcpSocketThread"
		var running = false
		var socket : Socket = null
		var reader : BufferedReader = null
		var writer : PrintWriter = null

		def init_socket() {
			Log.d(TAG, "init_socket()")
			this.synchronized {
				if (socket != null) {
					shutdown()
				}
				socket = new Socket(host, port)
				socket.setKeepAlive(true)
				reader = new BufferedReader(new InputStreamReader(
						socket.getInputStream()), 256)
				writer = new PrintWriter(new OutputStreamWriter(
						socket.getOutputStream()), true)
				writer.println(login + filter)
				running = true
			}
			Log.d(TAG, "init_socket() done")
		}

		override def run() {
			Log.d(TAG, "TcpSocketThread.run()")
			try {
				init_socket()
			} catch {
				case e : Exception => service.postAbort(e.getMessage())
			}
			while (running) {
				try {
					Log.d(TAG, "waiting for data...")
					var line : String = null
					while (running && { line = reader.readLine(); line != null }) {
						Log.d(TAG, "recv: " + line)
						if (line(0) != '#')
							service.postSubmit(line)
					}
					if (running && (line == null || !socket.isConnected())) {
						Log.d(TAG, "reconnecting in 30s")
						Thread.sleep(30*1000)
						init_socket()
					}
				} catch {
					case e : Exception => Log.d(TAG, "Exception" + e)
				}
			}
			Log.d(TAG, "TcpSocketThread.terminate()")
		}

		def update(packet : String) : String = {
			if (socket != null && socket.isConnected()) {
				writer.println(packet)
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
			this.synchronized {
				running = false
				catchLog("shutdownInput", socket.shutdownInput)
				catchLog("shutdownOutput", socket.shutdownOutput)
				catchLog("socket.close", socket.close)
			}
		}
	}
}
