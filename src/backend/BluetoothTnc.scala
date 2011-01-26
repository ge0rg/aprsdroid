package de.duenndns.aprsdroid

import _root_.android.bluetooth._
import _root_.android.app.Service
import _root_.android.content.{Intent, SharedPreferences}
import _root_.android.location.Location
import _root_.android.preference.PreferenceManager
import _root_.android.util.Log
import _root_.java.io.{BufferedReader, InputStreamReader, OutputStreamWriter, PrintWriter}
import _root_.java.net.{InetAddress, Socket}
import _root_.java.util.UUID

class BluetoothTnc(service : AprsService, prefs : SharedPreferences) extends AprsIsUploader(prefs) {
	val TAG = "BluetoothTnc"
	val SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

	val tncmac = prefs.getString("bt.mac", null)
	val tncchannel = prefs.getString("bt.channel", "")
	var conn : BtSocketThread = null

	createConnection()

	def start() {
	}

	def createConnection() {
		Log.d(TAG, "BluetoothTnc.createConnection: " + tncmac)
		val adapter = BluetoothAdapter.getDefaultAdapter()
		if (adapter == null) {
			service.postAbort("Bluetooth not supported!")
			return
		}
		if (!adapter.isEnabled()) {
			service.startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
		}

		conn = new BtSocketThread(adapter.getRemoteDevice(tncmac))
		conn.start()
	}

	def update(packet : String) : String = {
		Log.d(TAG, "BluetoothTnc.update: " + packet)
		conn.update(packet)
	}

	def stop() {
		conn.shutdown()
		conn.interrupt()
		conn.join()
	}

	class BtSocketThread(tnc : BluetoothDevice)
			extends Thread("APRSdroid Bluetooth connection") {
		val TAG = "BtSocketThread"
		var running = false
		var socket : BluetoothSocket = null
		var reader : BufferedReader = null
		var writer : PrintWriter = null

		def init_socket() {
			Log.d(TAG, "init_socket()")
			this.synchronized {
				if (socket != null) {
					shutdown()
				}
				if (tncchannel == "") {
					socket = tnc.createRfcommSocketToServiceRecord(SPP)
				} else {
					val m = tnc.getClass().getMethod("createRfcommSocket", classOf[Int])
					val chan = tncchannel.asInstanceOf[Int]
					socket = m.invoke(tnc, chan.asInstanceOf[AnyRef]).asInstanceOf[BluetoothSocket]
				}

				socket.connect()
				reader = new BufferedReader(new InputStreamReader(
						socket.getInputStream()), 256)
				writer = new PrintWriter(new OutputStreamWriter(
						socket.getOutputStream()), true)
				running = true
			}
			Log.d(TAG, "init_socket() done")
		}

		override def run() {
			Log.d(TAG, "BtSocketThread.run()")
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
				} catch {
					case e : Exception => 
						Log.d(TAG, "Exception" + e)
						Log.d(TAG, "reconnecting in 30s")
						try {
							Thread.sleep(30*1000)
							init_socket()
						} catch { case _ => }
				}
			}
			Log.d(TAG, "BtSocketThread.terminate()")
		}

		def update(packet : String) : String = {
			if (socket != null) {
				writer.println(packet)
				"Bluetooth OK"
			} else "Bluetooth disconnected"
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
				catchLog("socket.close", socket.close)
			}
		}
	}
}
