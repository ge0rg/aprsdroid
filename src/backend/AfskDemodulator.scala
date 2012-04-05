package org.aprsdroid.app

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log

import sivantoledo.ax25.Afsk1200Demodulator

class AfskDemodulator(au : AfskUploader, samplerate : Int) extends Thread("AFSK demodulator") {
	val TAG = "APRSdroid.AfskDemod"

	val BUF_SIZE = 8192
	val buffer_s = new Array[Short](BUF_SIZE)
	val buffer_f = new Array[Float](BUF_SIZE)

	val demod = new Afsk1200Demodulator(samplerate, 1, 6, au)
	val recorder = new AudioRecord(/*MediaRecorder.AudioSource.MIC = */ 1, samplerate,
				AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
				4*BUF_SIZE)

	// we process incoming audio
	android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)


	override def run() {
		Log.d(TAG, "running...")
		try {
			recorder.startRecording();
			while (!isInterrupted() && (recorder.getRecordingState() != AudioRecord.RECORDSTATE_STOPPED)) {
				val count = recorder.read(buffer_s, 0, BUF_SIZE)
				Log.d(TAG, "read " + count + " samples")
				if (count <= 0)
					throw new RuntimeException("recorder.read() = " + count)

				for (i <- 0 to count-1)
					buffer_f(i) = buffer_s(i).asInstanceOf[Float] / 32768.0f

				demod.addSamples(buffer_f, count)
			}
		} catch {
		case e : Exception =>
			Log.e(TAG, "run(): " + e)
			e.printStackTrace()
			au.postAbort(e.toString())
		}
		Log.d(TAG, "closed.")
	}

	def close() {
		try {
			this.interrupt()
			recorder.stop()
			this.join(50)
		} catch {
		case e : IllegalStateException => Log.w(TAG, "close(): " + e)
		}
	}
}
