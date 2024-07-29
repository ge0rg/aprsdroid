package org.aprsdroid.app

import _root_.android.media.{AudioManager, AudioTrack}

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.util.Log
import java.io.{InputStream, OutputStream}

import net.ab0oo.aprs.parser._
import com.nogy.afu.soundmodem.{Message, APRSFrame, Afsk}
import com.felhr.usbserial._
import com.jazzido.PacketDroid.{AudioBufferProcessor, PacketCallback}
import sivantoledo.ax25.PacketHandler

object DigiRig {
	def deviceHandle(dev : UsbDevice) = {
		"usb_%04x_%04x_%s".format(dev.getVendorId(), dev.getProductId(), dev.getDeviceName())
	}

	def checkDeviceHandle(prefs : SharedPreferences, dev_p : android.os.Parcelable) : Boolean = {
		if (dev_p == null)
			return false
		val dev = dev_p.asInstanceOf[UsbDevice]
		val last_use = prefs.getString(deviceHandle(dev), null)
		if (last_use == null)
			return false
		prefs.edit().putString("proto", last_use)
			    .putString("link", "usb").commit()
		true
	}
}

class DigiRig(service : AprsService, prefs : PrefsWrapper) extends AfskUploader(service, prefs)
	with PacketHandler with PacketCallback {
	override val TAG = "APRSdroid.Digirig"

	// USB stuff
	val USB_PERM_ACTION = "org.aprsdroid.app.DigiRig.PERM"
	val ACTION_USB_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED"
	val ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED"

	val usbManager = service.getSystemService(Context.USB_SERVICE).asInstanceOf[UsbManager];
	var thread : UsbThread = null
	var dev : UsbDevice = null
	var con : UsbDeviceConnection = null
	var ser : UsbSerialInterface = null
	var alreadyRunning = false

	val intent = new Intent(USB_PERM_ACTION)
	val pendingIntent = PendingIntent.getBroadcast(service, 0, intent, 0)

	// Audio stuff
	output.setVolume(AudioTrack.getMaxVolume())

	val receiver = new BroadcastReceiver() {
		override def onReceive(ctx: Context, i: Intent) {
			Log.d(TAG, "onReceive: " + i)
			if (i.getAction() == ACTION_USB_DETACHED) {
				log("USB device detached.")
				ctx.stopService(AprsService.intent(ctx, AprsService.SERVICE))
				return
			}
			val granted = i.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED)
			if (!granted) {
				service.postAbort(service.getString(R.string.p_serial_noperm))
				return
			}
			log("Obtained USB permissions.")
			thread = new UsbThread()
			thread.start()
		}
	}

	override val btScoReceiver = new BroadcastReceiver() {
		override def onReceive(ctx : Context, i : Intent) {
			Log.d(TAG, "onReceive: " + i)
			if (i.getAction() == ACTION_USB_DETACHED) {
				log("USB device detached.")
				ctx.stopService(AprsService.intent(ctx, AprsService.SERVICE))
				return
			}
			val granted = i.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED)
			if (!granted) {
				service.postAbort(service.getString(R.string.p_serial_noperm))
				return
			}
			log("Obtained USB permissions.")
			thread = new UsbThread()
			thread.start()

			val state = i.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
			Log.d(TAG, "AudioManager SCO event: " + state)
			if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
				// we are connected, perform actual start
				log(service.getString(R.string.afsk_info_sco_est))
				aw.start()
				service.unregisterReceiver(this)
				service.postPosterStarted()
			}
		}
	}

	var proto : TncProto = null
	var sis : SerialInputStream = null

	override def start() = {
		val filter = new IntentFilter(USB_PERM_ACTION)
		filter.addAction(ACTION_USB_DETACHED)
		service.registerReceiver(receiver, filter)
		alreadyRunning = true
		if (ser == null)
			requestPermissions()

		if (!isCallsignAX25Valid())
			false

		if (use_bt) {
			log(service.getString(R.string.afsk_info_sco_req))
			service.getSystemService(Context.AUDIO_SERVICE)
				.asInstanceOf[AudioManager].startBluetoothSco()
			service.registerReceiver(btScoReceiver, new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED))
			false
		} else {
			aw.start()
			true
		}

		false
	}

	def requestPermissions() {
		Log.d(TAG, "Digirig.requestPermissions");
		val dl = usbManager.getDeviceList();
		var requested = false
		import scala.collection.JavaConversions._
		for ((name, dev) <- dl) {
			val deviceVID = dev.getVendorId()
			val devicePID = dev.getProductId()
			if (UsbSerialDevice.isSupported(dev)) {
				// this is not a USB Hub
				log("Found USB device %04x:%04x, requesting permissions.".format(deviceVID, devicePID))
				this.dev = dev
				usbManager.requestPermission(dev, pendingIntent)
				return
			} else
				log("Unsupported USB device %04x:%04x.".format(deviceVID, devicePID))
		}
		service.postAbort(service.getString(R.string.p_serial_notfound))
	}

	override def update(packet: APRSPacket): String = {
		// Need to "parse" the packet in order to replace the Digipeaters
		packet.setDigipeaters(Digipeater.parseList(Digis, true))
		val from = packet.getSourceCall()
		val to = packet.getDestinationCall()
		val data = packet.getAprsInformation().toString()
		val msg = new APRSFrame(from, to, Digis, data, FrameLength).getMessage()
		Log.d(TAG, "update(): From: " + from + " To: " + to + " Via: " + Digis + " telling " + data)

		ser.setRTS(true)
		val bits_per_byte = 8
		val bits_in_frame = packet.toAX25Frame().length / bits_per_byte
		val ms_per_s = 1000
		val sleep_ms = bits_in_frame * ms_per_s / 1200 // aprs is 1200 baud
		val sleep_pad_ms = 1500
		Thread.sleep(sleep_ms + sleep_pad_ms)
		val result = sendMessage(msg)
		Thread.sleep(sleep_ms + sleep_pad_ms)
		ser.setRTS(false)

		if (result)
			"AFSK OK"
		else
			"AFSK busy"
	}

	override def stop() {
                // Stop USB thread
		if (alreadyRunning)
			service.unregisterReceiver(receiver)
		alreadyRunning = false
		if (ser != null)
			ser.close()
		if (sis != null)
			sis.close()
		if (con != null)
			con.close()
		if (thread == null)
			return
		thread.synchronized {
			thread.running = false
		}
		thread.interrupt()
		thread.join(50)

                // Stop AFSK Demodulator
		aw.close()
		if (use_bt) {
			service.getSystemService(Context.AUDIO_SERVICE)
				.asInstanceOf[AudioManager].stopBluetoothSco()
			try {
				service.unregisterReceiver(btScoReceiver)
			} catch {
				case e : RuntimeException => // ignore, receiver already unregistered
			}
		}
	}

	class UsbThread() extends Thread("APRSdroid USB connection") {
		val TAG = "UsbThread"
		var running = true

		def log(s : String) {
			service.postAddPost(StorageDatabase.Post.TYPE_INFO, R.string.post_info, s)
		}

		override def run() {
			val con = usbManager.openDevice(dev)
			ser = UsbSerialDevice.createUsbSerialDevice(dev, con)
			if (ser == null || !ser.syncOpen()) {
				con.close()
				service.postAbort(service.getString(R.string.p_serial_unsupported))
				return
			}
			val baudrate = prefs.getStringInt("baudrate", 115200)
			ser.setBaudRate(baudrate)
			ser.setDataBits(UsbSerialInterface.DATA_BITS_8)
			ser.setStopBits(UsbSerialInterface.STOP_BITS_1)
			ser.setParity(UsbSerialInterface.PARITY_NONE)
			ser.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF)
			ser.setRTS(false)

			// success: remember this for usb-attach launch
			prefs.prefs.edit().putString(UsbTnc.deviceHandle(dev), prefs.getString("proto", "afsk")).commit()

			log("Opened " + ser.getClass().getSimpleName() + " at " + baudrate + "bd")
			service.postPosterStarted()
			while (running) { /* do nothing */ }
			Log.d(TAG, "terminate()")
		}
	}
}
