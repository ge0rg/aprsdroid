package org.aprsdroid.app

import _root_.android.bluetooth._
import _root_.android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import _root_.android.util.Log

import _root_.java.util.UUID
import _root_.android.bluetooth.BluetoothGattCharacteristic
import _root_.android.bluetooth.BluetoothGattCallback
import _root_.android.bluetooth.BluetoothGatt
import _root_.android.bluetooth.BluetoothDevice
import _root_.net.ab0oo.aprs.parser._
import android.os.{Build, Handler, Looper}

import java.io._
import java.util.concurrent.Semaphore

// This requires API level 21 at a minimum, API level 23 to work with dual-mode devices.
class BluetoothLETnc(service : AprsService, prefs : PrefsWrapper) extends AprsBackend(prefs) {
	private val TAG = "APRSdroid.BluetoothLE"

	private val SERVICE_UUID = UUID.fromString("00000001-ba2a-46c9-ae49-01b0961f68bb")
	private val CHARACTERISTIC_UUID_RX = UUID.fromString("00000003-ba2a-46c9-ae49-01b0961f68bb")
	private val CHARACTERISTIC_UUID_TX = UUID.fromString("00000002-ba2a-46c9-ae49-01b0961f68bb")

	private val tncmac = prefs.getString("ble.mac", null)
	private var gatt: BluetoothGatt = null
	private var tncDevice: BluetoothDevice = null
	private var txCharacteristic: BluetoothGattCharacteristic = null
	private var rxCharacteristic: BluetoothGattCharacteristic = null

	private var proto: TncProto = _

	private val bleInputStream = new BLEInputStream()
	private val bleOutputStream = new BLEOutputStream()

	private var conn : BLEReceiveThread = null

	private var reconnect = true

	private var mtu = 20 // Default BLE MTU (-3)

	override def start(): Boolean = {
		if (gatt == null)
			createConnection()
		false
	}

	private def connect(): Unit = {
		// Must use application context here, otherwise authorization dialogs always fail, and
		// other GATT operations intermittently fail.
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			gatt = tncDevice.connectGatt(service.getApplicationContext, false, callback, BluetoothDevice.TRANSPORT_LE)
		} else {
			// Dual-mode devices are not supported
			gatt = tncDevice.connectGatt(service.getApplicationContext, false, callback)
		}
	}

	private val callback = new BluetoothGattCallback {
		override def onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int): Unit = {
			if (newState == BluetoothProfile.STATE_CONNECTED) {
				Log.d(TAG, "Connected to GATT server")
				BluetoothLETnc.this.gatt = gatt
				gatt.discoverServices()
			} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
				// For BLE devices, there is a 3-phase pairing process on Android, requiring that the user
				// twice approve pairing to the device. (At least this is true for dual-mode devices with
				// an encrypted characteristic.)
				//
				// Phase one is bonding, which must be done by the user using the device's Bluetooth
				// Settings interface.
				//
				// During phase 2, the device is disconnected with an authorization error while attempting
				// to access a secured resource. An authorization is initiated automatically, and the
				// connection must be re-established.
				//
				// For dual mode devices, it may be necessary to "forget" the device in order for a BLE
				// connection to be established. When that happens, the TNC will be in an unbonded state.
				// The user must create the bond before attempting to connect.
				//
				// To recap:
				//  1. Bond the device (by user, in Bluetooth Settings)
				//  2. Grant access to characteristics requiring authorization
				//  3. Establish connection
				//
				// Steps 1 and 2 typically occur once (or any time the device is "forgotten").
				if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION) {
					gatt.close()
					BluetoothLETnc.this.gatt = null
					if (tncDevice.getBondState == BluetoothDevice.BOND_NONE) {
						service.postAbort(service.getString(R.string.bt_error_no_tnc))
					} else {
						// The second phase of the pairing process will occur the first time an encrypted
						// BLE characteristic is touched. This typically occurs when enabling notification
						// on the RX characteristic, in onDescriptorWrite(). When that happens, we need to
						// close the GATT connection and reconnect. A pairing dialog *may* appear.
						Log.w(TAG, "Authorization error")
						// Only try once. We don't want to spin endlessly when there is a problem. And there
						// will be problems. When we do spin endlessly here, the only solution is for the
						// user to disable and re-enable Bluetooth on the device. ¯\_(ツ)_/¯
						if (reconnect) {
							reconnect = false
							connect()
						} else {
							service.postAbort(service.getString(R.string.bt_error_connect, tncDevice.getName))
						}
					}
				} else {
					Log.d(TAG, "Disconnected from GATT server")
				}
			}
		}

		override def onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int): Unit = {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				Log.d(TAG, "Notification enabled")
				if (!gatt.requestMtu(517)) { // This requires API Level 21
					Log.e(TAG, "Could not request MTU change")
					service.postAbort(service.getString(R.string.bt_error_connect, tncDevice.getName))
				}
			} else {
				Log.e(TAG, f"Failed to write descriptor, status = $status")
			}
		}

		override def onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int): Unit = {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				Log.d(TAG, s"MTU changed to $mtu bytes")
				BluetoothLETnc.this.mtu = mtu - 3
			} else {
				Log.e(TAG, "Failed to change MTU")
			}
			// Once the MTU callback is complete, whether successful or not, we're ready to rock & roll.
			// Start the receive thread and instantiate the protocol adapter.
			conn.start()
			proto = AprsBackend.instanciateProto(service, bleInputStream, bleOutputStream)
		}

		override def onServicesDiscovered(gatt: BluetoothGatt, status: Int): Unit = {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				val gservice = gatt.getService(SERVICE_UUID)
				if (gservice != null) {
					txCharacteristic = gservice.getCharacteristic(CHARACTERISTIC_UUID_TX)
					rxCharacteristic = gservice.getCharacteristic(CHARACTERISTIC_UUID_RX)

					if (!gatt.setCharacteristicNotification(rxCharacteristic, true)) {
						Log.e(TAG, "Could not enable notification on RX Characteristic")
						service.postAbort(service.getString(R.string.bt_error_connect, tncDevice.getName))
						return
					}
					val descriptor = rxCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
					if (descriptor != null) {
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
							if (gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) != BluetoothStatusCodes.SUCCESS) {
								Log.e(TAG, "Could not write descriptor on RX Characteristic")
								service.postAbort(service.getString(R.string.bt_error_connect, tncDevice.getName))
								return
							}
						} else {
							descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
							if (!gatt.writeDescriptor(descriptor)) {
								Log.e(TAG, "Could not write descriptor on RX Characteristic")
								service.postAbort(service.getString(R.string.bt_error_connect, tncDevice.getName))
								return
							}
						}
					}
					Log.d(TAG, "Services discovered and characteristics set")
				} else {
					Log.e(TAG, "KISS service not found")
					service.postAbort(service.getString(R.string.bt_error_connect, tncDevice.getName))
				}
			} else {
				Log.d(TAG, "onServicesDiscovered received: " + status)
			}
		}

		override def onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int): Unit = {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				Log.d(TAG, "Characteristic write successful")
			} else {
				Log.d(TAG, "Characteristic write failed with status: " + status)
			}

			bleOutputStream.sent()
		}

		override def onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic): Unit = {
			val data = characteristic.getValue
			Log.d(TAG, "onCharacteristicChanged: " + data.length + " bytes from BLE")
			bleInputStream.appendData(data)
		}
	}

	private def createConnection(): Unit = {
		Log.d(TAG, "BluetoothTncBle.createConnection: " + tncmac)
		val adapter = BluetoothAdapter.getDefaultAdapter

		// Lollipop may work for BLE-only devices.
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
			service.postAbort(service.getString(R.string.bt_error_unsupported))
			return
		}

		if (adapter == null) {
			service.postAbort(service.getString(R.string.bt_error_unsupported))
			return
		}

		if (!adapter.isEnabled) {
			service.postAbort(service.getString(R.string.bt_error_disabled))
			return
		}

		if (tncmac == null) {
			service.postAbort(service.getString(R.string.bt_error_no_tnc))
			return
		}

		tncDevice = BluetoothAdapter.getDefaultAdapter.getRemoteDevice(tncmac)
		if (tncDevice == null) {
			service.postAbort(service.getString(R.string.bt_error_no_tnc))
			return
		}

		reconnect = true
		connect()
		conn = new BLEReceiveThread()
	}

	override def update(packet: APRSPacket): String = {
		try {
			proto.writePacket(packet)
			"BLE OK"
		} catch {
			case e: Exception =>
				e.printStackTrace()
				gatt.disconnect()
				"BLE disconnected"
		}
	}

	private def sendToBle(data: Array[Byte]): Unit = {
		if (txCharacteristic != null && gatt != null) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				gatt.writeCharacteristic(txCharacteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
			} else {
				txCharacteristic.setValue(data)
				gatt.writeCharacteristic(txCharacteristic)
			}
		}
	}

	override def stop(): Unit = {
		if (gatt == null)
			return

		conn.returnFreq()
			
		gatt.disconnect()
		gatt.close()
		gatt = null
		
		conn.synchronized {
			conn.running = false
		}
		conn.shutdown()
		conn.interrupt()
		conn.join(50)
	}

	private class BLEReceiveThread extends Thread("APRSdroid Bluetooth connection") {
		private val TAG = "APRSdroid.BLEReceiveThread"
		var running = true

		def returnFreq() {
			Log.d(TAG, "Return Frq")

			try {
				val backendName = service.prefs.getBackendName()
				// Check if the conditions for frequency control are met
				if (service.prefs != null && backendName != null &&
					service.prefs.getBoolean("freq_control", false) && backendName.contains("Bluetooth")) {					
					proto.writeReturn() // Send return command
					Log.d(TAG, "Return Issued")
				}					
			} catch {
				case e: Exception =>
					Log.e(TAG, "Error while sending return frequency command.", e)
			}
		}	

		override def run(): Unit = {
			running = true
			Log.d(TAG, "BLEReceiveThread.run()")

			try {
				// Attempt to start the poster (with exception handling)
				service.postPosterStarted()
			} catch {
				case e: Exception =>
					Log.d("ProtoTNC", "Exception in postPosterStarted: " + e.getMessage)
			}

			while (running) {
				try {
					// Log.d(TAG, "waiting for data...")
					while (running) {
						val line = proto.readPacket()
						Log.d(TAG, "recv: " + line)
						service.postSubmit(line)
					}
				} catch {
					case e : Exception =>
						Log.d("ProtoTNC", "proto.readPacket exception")
				}

			}
			Log.d(TAG, "BLEReceiveThread.terminate()")
		}

		def shutdown(): Unit = {
			Log.d(TAG, "shutdown()")
		}
	}

	private class BLEInputStream extends InputStream {
		private var buffer: Array[Byte] = Array()
		private val bytesAvailable = new Semaphore(0, true)

		def appendData(data: Array[Byte]): Unit = {
			buffer.synchronized {
				buffer ++= data
				bytesAvailable.release(data.length)
			}
		}

		override def read(): Int = {
			try {
				bytesAvailable.acquire(1)
				buffer.synchronized {
					val byte = buffer.head
					buffer = buffer.tail
					byte & 0xFF
				}
			} catch {
				case e : InterruptedException =>
					Log.d(TAG, "read() interrupted")
					-1
			}
		}

		override def read(b: Array[Byte], off: Int, len: Int): Int = {
			try {
				bytesAvailable.acquire(1)
				buffer.synchronized {
					val size = math.min(len, buffer.length)
					// Expect that we have at lease size - 1 permits available.
					if (bytesAvailable.tryAcquire(size - 1)) {
						System.arraycopy(buffer, 0, b, off, size)
						buffer = buffer.drop(size)
						size
					} else {
						// We have one...
						Log.e(TAG, "invalid number of semaphore permits")
						val head = buffer.head
						buffer = buffer.tail
						System.arraycopy(Array(head), 0, b, off, 1)
						1
					}
				}
			} catch {
				case e : InterruptedException =>
					Log.d(TAG, "read() interrupted")
					-1
			}
		}
	}

	private class BLEOutputStream extends OutputStream {
		private var buffer: Array[Byte] = Array()
		private var isWaitingForAck = false

		override def write(b: Int): Unit = {
			Log.d(TAG, f"write 0x$b%02X")
			buffer.synchronized {
				buffer ++= Array(b.toByte)
			}
		}

		private def valueOf(bytes : Array[Byte]) = bytes.map{
			b => String.format("%02X", new java.lang.Integer(b & 0xff))
		}.mkString

		override def write(b: Array[Byte], off: Int, len: Int): Unit = {
			val data = b.slice(off, off + len)
			Log.d(TAG, "write 0x" + valueOf(data))
			buffer.synchronized {
				buffer ++= data
			}
		}

		override def flush(): Unit = {
			Log.d(TAG, "Flushed. Send to BLE")

			isWaitingForAck.synchronized {
				if (!isWaitingForAck) {
					send()
					isWaitingForAck = true
				}
			}
		}

		private def send(): Int = {
			buffer.synchronized {
				if (!buffer.isEmpty) {
					val chunk = buffer.take(mtu)
					buffer = buffer.drop(mtu)
					sendToBle(chunk)
					return chunk.size
				} else {
					return 0
				}
			}
		}

		def sent(): Unit = {
			isWaitingForAck.synchronized {
				if (send() == 0) {
					isWaitingForAck = false
				}
			}
		}
	}
}
