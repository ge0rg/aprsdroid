package org.aprsdroid.app

import _root_.android.bluetooth._
import _root_.android.app.Service
import _root_.android.content.Intent
import _root_.android.location.Location
import _root_.android.util.Log
import _root_.java.io.{InputStream, OutputStream}
import _root_.java.net.{InetAddress, Socket}
import _root_.java.util.UUID

import _root_.net.ab0oo.aprs.parser._

class BluetoothTnc(service : AprsService, prefs : PrefsWrapper) extends AprsBackend(prefs) {
	val TAG = "APRSdroid.Bluetooth"
	val SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

	val bt_client = prefs.getBoolean("bt.client", true)
	val tncmac = prefs.getString("bt.mac", null)
	val tncchannel = prefs.getStringInt("bt.channel", -1)
	var digipath = prefs.getString("digi_path", "WIDE1-1")
	var conn : BtSocketThread = null

	def start() = {
		if (conn == null)
			createConnection()
		false
	}

	def createTncProto(is : InputStream, os : OutputStream) : TncProto =
		new KissProto(is, os, digipath)

	def createConnection() {
		Log.d(TAG, "BluetoothTnc.createConnection: " + tncmac)
		val adapter = BluetoothAdapter.getDefaultAdapter()
		if (adapter == null) {
			service.postAbort(service.getString(R.string.bt_error_unsupported))
			return
		}
		if (!adapter.isEnabled()) {
			service.postAbort(service.getString(R.string.bt_error_disabled))
			return
		}
		if (bt_client && tncmac == null) {
			service.postAbort(service.getString(R.string.bt_error_no_tnc))
			return
		}

		val tnc = if (bt_client) adapter.getRemoteDevice(tncmac) else null
		conn = new BtSocketThread(adapter, tnc)
		conn.start()
	}

	def update(packet : APRSPacket) : String = {
		// the digipeater setting here is a duplicate just for log purpose
		packet.setDigipeaters(Digipeater.parseList(digipath, true))
		Log.d(TAG, "BluetoothTnc.update: " + packet)
		conn.update(packet)
	}

	def stop() {
		if (conn == null)
			return
		conn.shutdown()
		conn.interrupt()
		conn.join(50)
	}

	class BtSocketThread(ba : BluetoothAdapter, tnc : BluetoothDevice)
			extends Thread("APRSdroid Bluetooth connection") {
		val TAG = "BtSocketThread"
		var running = true
		var socket : BluetoothSocket = null
		var proto : TncProto = null

		def log(s : String) {
			Log.i(TAG, s)
			service.postAddPost(StorageDatabase.Post.TYPE_INFO, R.string.post_info, s)
		}

		def init_socket() {
			Log.d(TAG, "init_socket()")
			if (socket != null) {
				shutdown()
			}
				if (tnc == null) {
					// we are a host
					log("Awaiting client...")
					socket = ba.listenUsingRfcommWithServiceRecord("SPP", SPP).accept(-1)
					log("Client connected.")
				} else
				if (tncchannel == -1) {
					log("Connecting to SPP service on %s...".format(tncmac))
					socket = tnc.createRfcommSocketToServiceRecord(SPP)
					socket.connect()
				} else {
					log("Connecting to channel %d...".format(tncchannel))
					val m = tnc.getClass().getMethod("createRfcommSocket", classOf[Int])
					socket = m.invoke(tnc, tncchannel.asInstanceOf[AnyRef]).asInstanceOf[BluetoothSocket]
					socket.connect()
				}

			this.synchronized {
				proto = createTncProto(socket.getInputStream(), socket.getOutputStream())
			}
			val initstring = prefs.getString("bt.init", null)
			val initdelay = prefs.getStringInt("bt.delay", 300)
			if (initstring != null && initstring != "") {
				log("Sending init: " + initstring)
				val os = socket.getOutputStream()
				for (line <- initstring.split("\n")) {
					os.write(line.getBytes())
					os.write('\r')
					os.write('\n')
					Thread.sleep(initdelay)
				}
			}
			Log.d(TAG, "init_socket() done")
		}

		override def run() {
			Log.d(TAG, "BtSocketThread.run()")
			try {
				init_socket()
				service.postPosterStarted()
			} catch {
				case e : Exception => e.printStackTrace(); service.postAbort(e.toString()); running = false;
			}
			while (running) {
				try {
					Log.d(TAG, "waiting for data...")
					while (running) {
						val line = proto.readPacket()
						Log.d(TAG, "recv: " + line)
						service.postSubmit(line)
					}
				} catch {
					case e : Exception => 
						try {
							Log.e(TAG, "reader exception: " + e)
							e.printStackTrace()
						} catch { case _ => Log.d(TAG, "Yo dawg! I got an exception while getting an exception!")
						}
						log("Reconnecting in 3s...")
						try {
							Thread.sleep(3*1000)
							init_socket()
						} catch { case _ => }
				}
			}
			Log.d(TAG, "BtSocketThread.terminate()")
		}

		def update(packet : APRSPacket) : String = {
			try {
				proto.writePacket(packet)
				"Bluetooth OK"
			} catch { case e => e.printStackTrace(); "Bluetooth disconnected" }
		}

		def catchLog(tag : String, fun : ()=>Unit) {
			Log.d(TAG, "catchLog(" + tag + ")")
			try {
				fun()
			} catch {
			case e : Exception => e.printStackTrace(); Log.d(TAG, tag + " execption: " + e)
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
