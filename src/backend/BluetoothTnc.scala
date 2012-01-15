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

class BluetoothTnc(service : AprsService, prefs : PrefsWrapper) extends AprsIsUploader(prefs) {
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
		var reader : KissReader = null
		var writer : KissWriter = null

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
				reader = new KissReader(socket.getInputStream())
				writer = new KissWriter(socket.getOutputStream())
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
						val line = reader.readPacket()
						Log.d(TAG, "recv: " + line)
						service.postSubmit(line)
					}
				} catch {
					case e : Exception => 
						e.printStackTrace()
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
				writer.writePacket(packet.toAX25Frame())
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
		def writePacket(p : Array[Byte]) {
			Log.d(TAG, "KissWriter.writePacket: %s".format(new String(p)))
			os.write(Kiss.FEND)
			os.write(Kiss.CMD_DATA)
			os.write(p)
			os.write(Kiss.FEND)
			os.flush()
		}
	}
}
