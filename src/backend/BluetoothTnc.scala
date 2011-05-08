package org.aprsdroid.app

import _root_.android.bluetooth._
import _root_.android.app.Service
import _root_.android.content.Intent
import _root_.android.location.Location
import _root_.android.util.Log
import _root_.java.io.{InputStream, OutputStream}
import _root_.java.net.{InetAddress, Socket}
import _root_.java.util.UUID

class BluetoothTnc(service : AprsService, prefs : PrefsWrapper) extends AprsIsUploader(prefs) {
	val TAG = "BluetoothTnc"
	val SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

	val tncmac = prefs.getString("bt.mac", null)
	val tncchannel = prefs.getStringInt("bt.channel", -1)
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
			service.postAbort("Bluetooth not enabled!")
			return
		}

		conn = new BtSocketThread(adapter.getRemoteDevice(tncmac))
		conn.start()
	}

	def update(packet : String) : String = {
		Log.d(TAG, "BluetoothTnc.update: " + packet)
		conn.update(packet)
	}

	def stop() {
		if (conn == null)
			return
		conn.shutdown()
		conn.interrupt()
		conn.join()
	}

	class BtSocketThread(tnc : BluetoothDevice)
			extends Thread("APRSdroid Bluetooth connection") {
		val TAG = "BtSocketThread"
		var running = false
		var socket : BluetoothSocket = null
		var reader : KissReader = null
		var writer : KissWriter = null

		def init_socket() {
			Log.d(TAG, "init_socket()")
			this.synchronized {
				if (socket != null) {
					shutdown()
				}
				if (tncchannel == -1) {
					socket = tnc.createRfcommSocketToServiceRecord(SPP)
				} else {
					val m = tnc.getClass().getMethod("createRfcommSocket", classOf[Int])
					socket = m.invoke(tnc, tncchannel.asInstanceOf[AnyRef]).asInstanceOf[BluetoothSocket]
				}

				socket.connect()
				reader = new KissReader(socket.getInputStream())
				writer = new KissWriter(socket.getOutputStream())
				running = true
			}
			Log.d(TAG, "init_socket() done")
		}

		override def run() {
			Log.d(TAG, "BtSocketThread.run()")
			try {
				init_socket()
			} catch {
				case e : Exception => e.printStackTrace(); service.postAbort(e.toString())
			}
			while (running) {
				try {
					Log.d(TAG, "waiting for data...")
					while (running) {
						val line = reader.readPacket()
						Log.d(TAG, "recv: " + line)
						service.postSubmit(line)
					}
				} catch {
					case e : Exception => 
						e.printStackTrace()
						Log.d(TAG, "reconnecting in 3s")
						try {
							Thread.sleep(3*1000)
							init_socket()
						} catch { case _ => }
				}
			}
			Log.d(TAG, "BtSocketThread.terminate()")
		}

		def update(packet : String) : String = {
			if (socket != null) {
				writer.writePacket(packet)
				"Bluetooth OK"
			} else "Bluetooth disconnected"
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

	object Kiss {
		// escape sequences
		val FEND  = 0xC0
		val FESC  = 0xDB
		val TFEND = 0xDC
		val TFESC = 0xDD

		// commands
		val CMD_DATA = 0x00
	}

	class KissReader(is : InputStream) {
		def readPacket() : String = {
			import Kiss._
			val buf = scala.collection.mutable.ListBuffer[Byte]()
			do {
				var ch = is.read()
				Log.d(TAG, "KissReader.readPacket: %02X '%c'".format(ch, ch))
				ch match {
				case FEND =>
					if (buf.length > 0) {
						Log.d(TAG, "KissReader.readPacket: sending back %s".format(new String(buf.toArray)))
						try {
							return Parser.parseAX25(buf.toArray).toString().trim()
						} catch {
							case e => buf.clear()
						}
					}
				case FESC => is.read() match {
					case TFEND => buf.append(FEND.toByte)
					case TFESC => buf.append(FESC.toByte)
					case _ =>
					}
				case -1	=> throw new java.io.IOException("KissReader out of data")
				case 0 =>
					// hack: ignore 0x00 byte at start of frame, this is the command
					if (buf.length != 0)
						buf.append(ch.toByte)
					else
						Log.d(TAG, "KissReader.readPacket: ignoring command byte")
				case _ =>
					buf.append(ch.toByte)
				}
			} while (true)
			""
		}
	}

	class KissWriter(os : OutputStream) {
		def writePacket(p : String) {
			Log.d(TAG, "KissWriter.writePacket: %s".format(p))
			os.write(Kiss.FEND)
			os.write(Kiss.CMD_DATA)
			os.write(p.getBytes())
			os.write(Kiss.FEND)
			os.flush()
		}
	}
}
