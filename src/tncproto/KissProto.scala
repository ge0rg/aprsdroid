package org.aprsdroid.app

import _root_.android.util.Log
import _root_.java.io.{InputStream, OutputStream}

import _root_.net.ab0oo.aprs.parser._

class KissProto(service : AprsService, is : InputStream, os : OutputStream) extends TncProto(is, os) {
	val TAG = "APRSdroid.KissProto"

	object Kiss {
		// escape sequences
		val FEND  = 0xC0
		val FESC  = 0xDB
		val TFEND = 0xDC
		val TFESC = 0xDD
		// commands
		val CMD_DATA = 0x00
		val CONTROL_COMMAND = 0x06
		val FREQ = 0xEA
		val RETURN = 0xEB
	}

	val initstring = java.net.URLDecoder.decode(service.prefs.getString("kiss.init", ""), "UTF-8")
	val initdelay = service.prefs.getStringInt("kiss.delay", 300)
	if (initstring != null && initstring != "") {
		for (line <- initstring.split("\n")) {
			service.postAddPost(StorageDatabase.Post.TYPE_TX,
				R.string.p_tnc_init, line)
			os.write(line.getBytes())
			os.write('\r')
			os.write('\n')
			Thread.sleep(initdelay)
		}
	}

	val checkprefs = service.prefs.getBackendName()
	Log.d(TAG, s"Backend Name1: $checkprefs")

	if (service.prefs.getBoolean("freq_control", false) && service.prefs.getBackendName().contains("Bluetooth SPP")) {
	  Log.d(TAG, "Frequency control is enabled.")

	  // Fetch the frequency control value as a float (default to 0.0f if not found)
	  val freqMHZ = service.prefs.getStringFloat("frequency_control_value", 0.0f)
	  Log.d(TAG, s"Frequency control value fetched: $freqMHZ MHz")

	  // Use the freqConvert function to convert the frequency to a byte array
	  val freqBytes = freqConvert(freqMHZ)	
	  writeFreq(freqBytes)  // Send the entire array of bytes at once
	  
	  Log.d(TAG, s"Frequency in bytes (MSB first): ${freqBytes.map(b => f"0x$b%02X").mkString(" ")}")

	}

	if (service.prefs.getCallsign().length() > 6) {
		throw new IllegalArgumentException(service.getString(R.string.e_toolong_callsign))
	}

	def freqConvert(freqMHz: Float): Array[Byte] = {
	  // Convert frequency from MHz to Hz (float to integer)
	  val freqHz = (freqMHz * 1000000).toLong

	  // Convert the frequency to 32-bit (big-endian)
	  val bytes = Array[Byte](
		(freqHz >> 24).toByte,   // MSB
		(freqHz >> 16).toByte,
		(freqHz >> 8).toByte,
		freqHz.toByte            // LSB
	  )

	  val escapedBytes = bytes.flatMap { byte =>
		if (byte == Kiss.FEND.toByte) {
		  Array[Byte](Kiss.FESC.toByte, Kiss.TFEND.toByte)
		} else {
		  Array[Byte](byte)
		}
	  }
	  escapedBytes
	}

	def readPacket() : String = {
		import Kiss._
		val buf = scala.collection.mutable.ListBuffer[Byte]()
		do {
			var ch = is.read()
			if (ch >= 0)
				Log.d(TAG, "readPacket: %02X '%c'".format(ch, ch))
			ch match {
			case FEND =>
				if (buf.length > 0) {
					Log.d(TAG, "readPacket: sending back %s".format(new String(buf.toArray)))
					try {
						return Parser.parseAX25(buf.toArray).toString().trim()
					} catch {
						case e : Exception => buf.clear()
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
					Log.d(TAG, "readPacket: ignoring command byte")
			case 10 =>
				// heuristic for ASCII strings:
				//   * non-empty (including CRLF)
				//   * starts with ASCII character (KISS starts with >=0x82)
				//     (buf(0) > 0) does this check, as byte is [-128..127]
				//   * ends in CRLF
				if (buf.length > 1 && (buf(0) > 0) && buf(buf.length-1)==13)
					return new String(buf.toArray).trim()
			case _ =>
				buf.append(ch.toByte)
			}
		} while (true)
		""
	}

	def writePacket(p : APRSPacket) {
		Log.d(TAG, "writePacket: " + p)
		val combinedData = Array[Byte](Kiss.FEND.toByte, Kiss.CMD_DATA.toByte) ++ p.toAX25Frame() ++ Array[Byte](Kiss.FEND.toByte)
		os.write(combinedData)
		os.flush()
	}

	def writeFreq(freqBytes: Array[Byte]): Unit = {
	  val frame = Array[Byte](
		Kiss.FEND.toByte,
		Kiss.CONTROL_COMMAND.toByte,
		Kiss.FREQ.toByte      // Frequency byte
	  ) ++ freqBytes ++ Array[Byte](Kiss.FEND.toByte)
	  
	  os.write(frame)
	  os.flush()
	}
}
