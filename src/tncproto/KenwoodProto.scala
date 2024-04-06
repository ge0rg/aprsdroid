package org.aprsdroid.app

import _root_.android.content.Context
import _root_.android.location._
import _root_.android.util.Log
import _root_.android.os.{Build, Bundle, Handler, Looper}
import _root_.java.io.{BufferedReader, InputStream, InputStreamReader, OutputStream, OutputStreamWriter}

import _root_.net.ab0oo.aprs.parser._

class KenwoodProto(service : AprsService, is : InputStream, os : OutputStream) extends TncProto(is, null) {
	val TAG = "APRSdroid.KenwoodProto"
	val br = new BufferedReader(new InputStreamReader(is))
	val sinkhole = new LocationSinkhole()
	val locMan = service.getSystemService(Context.LOCATION_SERVICE).asInstanceOf[LocationManager]
	val output = new OutputStreamWriter(os)

	var listenerR5 : NmeaListenerR5 = null
	var listenerR24 : NmeaListenerR24 = null

        if (service.prefs.getBoolean("kenwood.gps", false)) {
                new Handler(Looper.getMainLooper()).post(new Runnable() { override def run() {
                        locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                                0, 0, sinkhole)
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
				listenerR5 = new NmeaListenerR5()
				locMan.addNmeaListener(listenerR5)
			} else {
				// TODO: if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
				listenerR24 = new NmeaListenerR24()
				locMan.addNmeaListener(listenerR24)
			}
                }})
        }

	def wpl2aprs(line : String) = {
		val s = line.split("[,*]") // get and split nmea
		s(0) match {
		case "$PKWDWPL" =>
			val lat = "%s%s".format(s(3), s(4))
			val lon = "%s%s".format(s(5), s(6))
			val call = s(11).trim()
			val sym = s(12)
			"%s>APRS:!%s%s%s%s".format(call, lat, sym(0), lon, sym(1))
		case "$GPWPL" =>
			val lat = "%s%s".format(s(1), s(2))
			val lon = "%s%s".format(s(3), s(4))
			val call = s(5).trim()
			"%s>APRS:!%s/%s/".format(call, lat, lon)
		case _ => line.replaceFirst("^(cmd:)+", "") // workaround for Kenwood APRS mode
		}
	}

	// Solution for #141 - yaesu FTM-400XDR packet monitor
	def yaesu2aprs(line1 : String, line2 : String) = {
		Log.d(TAG, "line1: " + line1)
		Log.d(TAG, "line2: " + line2)
		// remove the timestamp and UI meta data from first line, concatenate with second line using ":"
		line1.replaceAll(" \\[[0-9/: ]+\\] <UI ?[A-Z]?>:$", ":") + line2
	}

	def readPacket() : String = {
		var line = br.readLine()
		// loop: read a non-empty line
		while (line == null || line.length() == 0)
			line = br.readLine()
		if (line.contains("] <UI") && line.endsWith(">:"))
			return yaesu2aprs(line, br.readLine())
		Log.d(TAG, "got " + line)
		return wpl2aprs(line)
	}

	def writePacket(p : APRSPacket) {
		// don't do anything. yet.
	}

	def onNmeaReceived(timestamp : Long, nmea : String) {
		if (output != null && (nmea.startsWith("$GPGGA") || nmea.startsWith("$GPRMC"))) {
			Log.d(TAG, "NMEA >>> " + nmea)
			try {
                                implicit val ec = scala.concurrent.ExecutionContext.global
                                scala.concurrent.Future {
                                        output.write(nmea)
                                        output.flush()
                                }
                                if (service.prefs.getBoolean("kenwood.gps_debug", false))
                                        service.postAddPost(StorageDatabase.Post.TYPE_TX,
                                                R.string.p_conn_kwd, nmea.trim())
			} catch {
			case e : Exception =>
				Log.e(TAG, "error sending NMEA to Kenwood: " + e)
				e.printStackTrace()
			}
		} else
			Log.d(TAG, "NMEA --- " + nmea)
	}

	class NmeaListenerR5 extends GpsStatus.NmeaListener() {
		def onNmeaReceived(timestamp : Long, nmea : String) = KenwoodProto.this.onNmeaReceived(timestamp, nmea)
	}

	class NmeaListenerR24 extends OnNmeaMessageListener() {
		def onNmeaMessage(nmea : String, timestamp : Long) = KenwoodProto.this.onNmeaReceived(timestamp, nmea)
	}

	class LocationSinkhole extends LocationListener {
	override def onLocationChanged(location : Location) {
	}

	override def onProviderDisabled(provider : String) {
	}
	override def onProviderEnabled(provider : String) {
	}
	override def onStatusChanged(provider : String, st: Int, extras : Bundle) {
	}
	}

	override def stop() {
		locMan.removeUpdates(sinkhole)
                if (listenerR5 != null)
                        locMan.removeNmeaListener(listenerR5)
                if (listenerR24 != null)
                        locMan.removeNmeaListener(listenerR24)
		super.stop()
	}
}

