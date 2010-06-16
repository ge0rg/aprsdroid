package de.duenndns.aprsdroid

import _root_.android.app.Service
import _root_.android.location.Location
import _root_.android.preference.PreferenceManager
import _root_.android.util.Log
import _root_.java.io.{BufferedReader, InputStreamReader, OutputStreamWriter, PrintWriter}
import _root_.java.net.{InetAddress, Socket}

class TcpUploader(service : AprsService, hostname : String, login : String, filter : String)
			extends AprsIsUploader(hostname, login) {
	val TAG = "TcpUploader"
	var is_connected : Boolean = false
	var socket : Socket = null
	var reader : BufferedReader = null
	var receiver : Thread = null
	var writer : PrintWriter = null

	createConnection()

	def start() {
	}

	def createConnection() {
		val (host, port) = AprsPacket.parseHostPort(hostname, 14580)
		Log.d(TAG, "TcpUploader.createConnection: " + host + ":" + port)
		if (socket != null)
			socket.close()
		socket = new Socket(host, port)
		socket.setKeepAlive(true)
		reader = new BufferedReader(new InputStreamReader(
				socket.getInputStream()))
		writer = new PrintWriter(new OutputStreamWriter(
				socket.getOutputStream()), true)
		writer.println(login + filter)
		if (receiver != null) {
			receiver.interrupt()
			receiver.join()
		}

		receiver = new TcpReceiver(service, reader)
		receiver.start()
	}

	def update(packet : String) : String = {
		Log.d(TAG, "TcpUploader.update: " + packet)
		writer.println(packet)
		"TCP OK"
	}

	def stop() {
		socket.close()
		reader.close()
		receiver.join()
		receiver = null
		socket = null
	}

	class TcpReceiver(service : AprsService, reader : BufferedReader) extends Thread("APRSdroid TcpReceiver") {
		override def run() {
			Log.d(TAG, "TcpReceiver.run()")
			try {
				var line : String = null
				while ({ line = reader.readLine(); line != null }) {
					Log.d(TAG, "recv: " + line)
					if (line(0) != '#')
						service.postSubmit(line)
				}
			} catch {
				case e : Exception => Log.d(TAG, "Exception" + e)
			}
			Log.d(TAG, "TcpReceiver.terminate()")
		}
	}
}
