package org.aprsdroid.app

import _root_.android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import _root_.android.media.{AudioManager, AudioTrack}
import _root_.android.util.Log
import _root_.java.net.{InetAddress, DatagramSocket, DatagramPacket}
import _root_.net.ab0oo.aprs.parser.{APRSPacket, Digipeater, Parser}
import com.nogy.afu.soundmodem.{Message, APRSFrame, Afsk}

import com.jazzido.PacketDroid.{AudioBufferProcessor, PacketCallback}
import sivantoledo.ax25.PacketHandler

class AfskUploader(service : AprsService, prefs : PrefsWrapper) extends AprsBackend(prefs)
		with PacketHandler with PacketCallback {
	val TAG = "APRSdroid.Afsk"
	// frame prefix: bytes = milliseconds * baudrate / 8 / 1000
	var FrameLength = prefs.getStringInt("afsk.prefix", 200)*1200/8/1000
	var Digis = prefs.getString("digi_path", "WIDE1-1")
	val use_hq = prefs.getAfskHQ()
	val use_bt = prefs.getAfskBluetooth()
	val samplerate = if (use_bt) 16000 else 22050
	val out_type = prefs.getAfskOutput()
	val in_type = if (use_bt) /*VOICE_CALL*/1 else /*MIC*/1
	val output = new Afsk(out_type, samplerate)
	val aw = new AfskInWrapper(use_hq, this, in_type, samplerate/2) // 8000 / 11025

	output.setVolume(AudioTrack.getMaxVolume())
	
	val btScoReceiver = new BroadcastReceiver() {
		override def onReceive(ctx : Context, i : Intent) {
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

	def isCallsignAX25Valid() : Boolean = {
		if (prefs.getCallsign().length() > 6) {
			service.postAbort(service.getString(R.string.e_toolong_callsign))
			false
		} else
			true
	}

	def start() : Boolean = {
		if (!isCallsignAX25Valid())
			return false
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
	}

	def sendMessage(msg : Message) : Boolean = {
		output.sendMessage(msg)
	}

	def update(packet : APRSPacket) : String = {
		// Need to "parse" the packet in order to replace the Digipeaters
		packet.setDigipeaters(Digipeater.parseList(Digis, true))
		val from = packet.getSourceCall()
		val to = packet.getDestinationCall()
		val data = packet.getAprsInformation().toString()
		val msg = new APRSFrame(from,to,Digis,data,FrameLength).getMessage()
		Log.d(TAG, "update(): From: " + from +" To: "+ to +" Via: " + Digis + " telling " + data)
		if (sendMessage(msg))
			"AFSK OK"
		else
			"AFSK busy"
	}

	def stop() {
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

	def received(data : Array[Byte]) = handlePacket(data)

	def handlePacket(data : Array[Byte]) {
		try {
			service.postSubmit(Parser.parseAX25(data).toString().trim())
		} catch {
			case e : Exception =>
				Log.e(TAG, "bad packet: %s".format(data.map("%02x".format(_)).mkString(" "))); e.printStackTrace()
		}
	}

	def peak(peak_value : Short) = notifyMicLevel(peak_value / 330)

	def notifyMicLevel(level : Int) {
		val i = new Intent(AprsService.MICLEVEL)
		i.putExtra("level", level)
		service.sendBroadcast(i)
	}

	def log(s : String) {
		Log.i(TAG, s)
		service.postAddPost(StorageDatabase.Post.TYPE_INFO, R.string.post_info, s)
	}

	def postAbort(s : String) {
		service.postAbort(s)
	}
}
