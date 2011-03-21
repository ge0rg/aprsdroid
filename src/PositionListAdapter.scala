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
}

class PositionListAdapter(context : Context,
	mycall : String)
		extends SimpleCursorAdapter(context, R.layout.stationview, null, PositionListAdapter.LIST_FROM, PositionListAdapter.LIST_TO) {

	var my_lat = 0
	var my_lon = 0
	lazy val storage = StorageDatabase.open(context)

	reload()

	lazy val locReceiver = new LocationReceiver(new Handler(), () => {
			reload()
		})
	context.registerReceiver(locReceiver, new IntentFilter(AprsService.UPDATE))


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
		} else
			view.setBackgroundColor(0)
		val MCD = 1000000.
		android.location.Location.distanceBetween(my_lat/MCD, my_lon/MCD,
			lat/MCD, lon/MCD, dist)
		Log.d("PLS", "distance %d %d - %d %d = %1.2f".format(my_lat, my_lon, lat, lon, dist(0)/1000))
		distage.setText("%1.2f km : %3.0f°\n%s".format(dist(0)/1000., dist(1), age))
		super.bindView(view, context, cursor)
	}

	def updateMyLocation(lat : Int, lon : Int) {
		my_lat = lat
		my_lon = lon
		val new_cursor = storage.getNeighbors(my_lat, my_lon, "20")
		changeCursor(new_cursor)
	}

	def reload() {
		val cursor = storage.getStaPositions(mycall, "1")
		if (cursor.getCount() > 0) {
			cursor.moveToFirst()
			val lat = cursor.getInt(StorageDatabase.Position.COLUMN_LAT)
			val lon = cursor.getInt(StorageDatabase.Position.COLUMN_LON)
			updateMyLocation(lat, lon)
		}
		cursor.close()
	}
}
