package org.aprsdroid.app

import _root_.android.content._
import _root_.android.database.Cursor
import _root_.android.os.{Bundle, Handler}
import _root_.android.text.format.DateUtils
import _root_.android.util.Log
import _root_.android.view.View
import _root_.android.widget.{SimpleCursorAdapter, TextView}

object PositionListAdapter {
	import StorageDatabase.Position._
	val LIST_FROM = Array(CALL, COMMENT, QRG)
	val LIST_TO = Array(R.id.station_call, R.id.listmessage, R.id.station_qrg)

	val SINGLE = 0
	val NEIGHBORS = 1
	val SSIDS = 2
}

class PositionListAdapter(context : Context,
	mycall : String, targetcall : String, mode : Int)
		extends SimpleCursorAdapter(context, R.layout.stationview, null, PositionListAdapter.LIST_FROM, PositionListAdapter.LIST_TO) {

	var my_lat = 0
	var my_lon = 0
	lazy val storage = StorageDatabase.open(context)

	reload()

	lazy val locReceiver = new LocationReceiver(new Handler(), () => {
			reload()
		})
	context.registerReceiver(locReceiver, new IntentFilter(AprsService.UPDATE))

	def getAgeColor(ts : Long) : Int = {
		val DARK = Array(0xff, 0x60, 0x60, 0x40)
		val BRIGHT = Array(0xff, 0xff, 0xff, 0xc0)
		val MAX = 30*60*1000
		val delta = (System.currentTimeMillis - ts).toInt
		val factor = if (delta < MAX) delta else MAX
		val mix = DARK zip BRIGHT map (t => { t._2 - (t._2 - t._1)*factor/MAX } )
		mix.reduceLeft(_*256 + _)
	}

	// return compass bearing for a given value
	private val LETTERS = Array("N", "NE", "E", "SE", "S", "SW", "W", "NW")
	def getBearing(b : Double) = LETTERS(((b.toInt + 22 + 720) % 360) / 45)

	override def bindView(view : View, context : Context, cursor : Cursor) {
		import StorageDatabase.Position._

		// TODO: multidimensional mapping
		val distage = view.findViewById(R.id.station_distage).asInstanceOf[TextView]
		val call = cursor.getString(COLUMN_CALL)
		val ts = cursor.getLong(COLUMN_TS)
		val age = DateUtils.getRelativeTimeSpanString(context, ts)
		val lat = cursor.getInt(COLUMN_LAT)
		val lon = cursor.getInt(COLUMN_LON)
		val dist = Array[Float](0, 0)

		if (call == mycall) {
			view.setBackgroundColor(0x4020ff20)
		} else if (call == targetcall) {
			view.setBackgroundColor(0x402020ff)
		} else
			view.setBackgroundColor(0)
		distage.setTextColor(getAgeColor(ts))
		view.findViewById(R.id.station_call).asInstanceOf[TextView].setTextColor(getAgeColor(ts))
		view.findViewById(R.id.station_qrg).asInstanceOf[TextView].setTextColor(getAgeColor(ts))
		val MCD = 1000000.
		android.location.Location.distanceBetween(my_lat/MCD, my_lon/MCD,
			lat/MCD, lon/MCD, dist)
		distage.setText("%1.1f km %s\n%s".format(dist(0)/1000., getBearing(dist(1)), age))
		super.bindView(view, context, cursor)
	}

	def updateMyLocation(lat : Int, lon : Int) {
		import PositionListAdapter._
		my_lat = lat
		my_lon = lon
		val new_cursor = mode match {
			case SINGLE	=> storage.getStaPositions(targetcall, "1")
			case NEIGHBORS	=> storage.getNeighbors(mycall, my_lat, my_lon, System.currentTimeMillis - 30*60*1000, "20")
			case SSIDS	=> storage.getAllSsids(targetcall)
		}
		changeCursor(new_cursor)
	}

	def reload() {
		val cursor = storage.getStaPositions(mycall, "1")
		if (cursor.getCount() > 0) {
			cursor.moveToFirst()
			my_lat = cursor.getInt(StorageDatabase.Position.COLUMN_LAT)
			my_lon = cursor.getInt(StorageDatabase.Position.COLUMN_LON)
		}
		cursor.close()
		updateMyLocation(my_lat, my_lon)
	}

	def onDestroy() {
		context.unregisterReceiver(locReceiver)
		changeCursor(null)
	}

}
