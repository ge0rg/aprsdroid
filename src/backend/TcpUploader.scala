package org.aprsdroid.app

import _root_.android.app.Service
import _root_.android.location.{Location, LocationManager}
import _root_.android.util.Log
import _root_.java.io.{BufferedReader, InputStreamReader, OutputStreamWriter, PrintWriter}
import _root_.java.net.{InetAddress, Socket}
import _root_.net.ab0oo.aprs.APRSPacket

class TcpUploader(service : AprsService, prefs : PrefsWrapper) extends AprsIsUploader(prefs) {
	val TAG = "TcpUploader"
	val hostname = prefs.getString("tcp.server", "euro.aprs2.net")
	val so_timeout = prefs.getStringInt("tcp.sotimeout", 120)
	var conn : TcpSocketThread = null

	createConnection()

	def start() {
	}

	def setupFilter() : String = {
		val filterdist = prefs.getStringInt("tcp.filterdist", 50)
		val userfilter = prefs.getString("tcp.filter", "")
		val lastloc = AprsPacket.formatRangeFilter(
			service.locMan.getLastKnownLocation(LocationManager.GPS_PROVIDER), filterdist)
		if (filterdist == 0) return " filter %s %s".format(userfilter, lastloc)
				else return " filter m/%d %s %s".format(filterdist, userfilter, lastloc)
	}

	def createConnection() {
		val (host, port) = AprsPacket.parseHostPort(hostname, 14580)
		Log.d(TAG, "TcpUploader.createConnection: " + host + ":" + port)
		conn = new TcpSocketThread(host, port)
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
				socket = new Socket(host, port)
				socket.setKeepAlive(true)
				socket.setSoTimeout(so_timeout*1000)
				reader = new BufferedReader(new InputStreamReader(
						socket.getInputStream()), 256)
				writer = new PrintWriter(new OutputStreamWriter(
						socket.getOutputStream()), true)
				Log.d(TAG, login + setupFilter())
				writer.println(login + setupFilter())
				running = true
			}
			Log.d(TAG, "init_socket() done")
		}

		override def run() {
			var need_reconnect = false
			Log.d(TAG, "TcpSocketThread.run()")
			try {
				init_socket()
			} catch {
				case e : Exception => service.postAbort(e.toString())
			}
			while (running) {
				try {
					if (need_reconnect) {
						need_reconnect = false
						shutdown()
						init_socket()
					}
					Log.d(TAG, "waiting for data...")
					var line : String = null
					while (running && { line = reader.readLine(); line != null }) {
						Log.d(TAG, "recv: " + line)
						if (line(0) != '#')
							service.postSubmit(line)
					}
					if (running && (line == null || !socket.isConnected())) {
						Log.d(TAG, "reconnecting in 30s")
						shutdown()
						Thread.sleep(30*1000)
						init_socket()
					}
				} catch {
					case se : java.net.SocketTimeoutException =>
						Log.i(TAG, "restarting due to timeout")
						need_reconnect = true
					case e : Exception => Log.d(TAG, "Exception " + e)
				}
			}
			Log.d(TAG, "TcpSocketThread.terminate()")
		}

		def update(packet : APRSPacket) : String = {
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
				catchLog("shutdownInput", socket.shutdownInput)
				catchLog("shutdownOutput", socket.shutdownOutput)
				catchLog("socket.close", socket.close)
				socket = null
			}
		}
	}
}
