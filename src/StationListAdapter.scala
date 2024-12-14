package org.aprsdroid.app

import _root_.android.app.ListActivity
import _root_.android.content._
import _root_.android.database.Cursor
import _root_.android.os.{AsyncTask, Bundle, Handler}
import _root_.android.text.format.DateUtils
import _root_.android.util.Log
import _root_.android.view.View
import _root_.android.widget.{SimpleCursorAdapter, TextView}
import _root_.android.widget.FilterQueryProvider

object StationListAdapter {
  import StorageDatabase.Station._
  val LIST_FROM = Array(CALL, COMMENT, QRG)
  val LIST_TO = Array(R.id.station_call, R.id.listmessage, R.id.station_qrg)

  val SINGLE = 0
  val NEIGHBORS = 1
  val SSIDS = 2
}

class StationListAdapter(context : Context, prefs : PrefsWrapper,
  mycall : String, targetcall : String, mode : Int)
    extends SimpleCursorAdapter(context, R.layout.stationview, null, StationListAdapter.LIST_FROM, StationListAdapter.LIST_TO) {

  var my_lat = 0
  var my_lon = 0
  var reload_pending = 0
  lazy val storage = StorageDatabase.open(context)

  if (mode == StationListAdapter.NEIGHBORS)
    setFilterQueryProvider(getNeighborFilter())

  reload()

  lazy val locReceiver = new LocationReceiver2(load_cursor,
    replace_cursor, cancel_cursor)

  context.registerReceiver(locReceiver, new IntentFilter(AprsService.UPDATE))

  private val DARK = Array(0xff, 0x80, 0x80, 0x50)
  private val BRIGHT = Array(0xff, 0xff, 0xff, 0xe8)
  private val MAX = 30*60*1000
  def getAgeColor(ts : Long) : Int = {
    val delta = System.currentTimeMillis - ts
    // normalize the time difference to a value 0..30min [ms]
    val factor = if (delta < MAX) delta.toInt else MAX
    // linearly blend the individual RGB values using the factor
    val mix = DARK zip BRIGHT map (t => { t._2 - (t._2 - t._1)*factor/MAX } )
    // make a single int from the color array
    mix.reduceLeft(_*256 + _)
  }

  // return compass bearing for a given value
  private val LETTERS = Array("N", "NE", "E", "SE", "S", "SW", "W", "NW")
  def getBearing(b : Double) = LETTERS(((b.toInt + 22 + 720) % 360) / 45)

  def isWeatherMessage(aprsMessage: String): Boolean = {
    // Check for '_' symbol and WX data pattern
    aprsMessage.contains("_") && 
    aprsMessage.matches(".*g\\d{3}t\\d{3}.*h\\d{2}b\\d{5}.*")
  }

  def parseWXReport(report: String): String = {
    val wind = report.substring(0, 7)
    val gust = report.substring(7, 11)
    val temp = report.substring(11, 15)
    val rain1h = report.substring(15, 19)
    val rain24h = report.substring(19, 23)
    val humidity = report.substring(23, 26)
    val pressure = report.substring(26, 32)

    val windSpeed = if (wind == "000/000") "Calm" else s"${wind.drop(4)} km/h"
    val gustSpeed = if (gust == "g000") "None" else s"${gust.drop(1)} km/h"
    val temperature = f"${temp.drop(1).toInt / 10.0}%.1f°C"
    val rain1hAmount = f"${rain1h.drop(1).toInt / 10.0}%.1fmm"
    val rain24hAmount = f"${rain24h.drop(1).toInt / 10.0}%.1fmm"
    val humidityPercent = s"${humidity.drop(1)}%"
    val pressureValue = f"${pressure.drop(1).toInt / 10.0}%.1f hPa"

    s"Wind: $windSpeed, Gust: $gustSpeed, Temp: $temperature, Rain(1h/24h): $rain1hAmount/$rain24hAmount, Humidity: $humidityPercent, Pressure: $pressureValue"
  }

  override def bindView(view: View, context: Context, cursor: Cursor) {
    import StorageDatabase.Station._

    // TODO: multidimensional mapping
    val distage = view.findViewById(R.id.station_distage).asInstanceOf[TextView]
    val call = cursor.getString(COLUMN_CALL)
    val ts = cursor.getLong(COLUMN_TS)
    val age = DateUtils.getRelativeTimeSpanString(context, ts)
    val lat = cursor.getInt(COLUMN_LAT)
    val lon = cursor.getInt(COLUMN_LON)
    val qrg = cursor.getString(COLUMN_QRG)
    val comment = cursor.getString(COLUMN_COMMENT)
    val symbol = cursor.getString(COLUMN_SYMBOL)
    val dist = Array[Float](0, 0)

    if (call == mycall) {
      view.setBackgroundColor(0x4020ff20)
    } else if (call == targetcall) {
      view.setBackgroundColor(0x402020ff)
    } else
      view.setBackgroundColor(0)
    val color = getAgeColor(ts)
    distage.setTextColor(color)
    view.findViewById(R.id.station_call).asInstanceOf[TextView].setTextColor(color)
    view.findViewById(R.id.station_qrg).asInstanceOf[TextView].setTextColor(color)
    val qrg_visible = if (qrg != null && qrg != "") View.VISIBLE else View.GONE
    view.findViewById(R.id.station_qrg).asInstanceOf[View].setVisibility(qrg_visible)
    val MCD = 1000000.0
    android.location.Location.distanceBetween(my_lat/MCD, my_lon/MCD,
      lat/MCD, lon/MCD, dist)
    distage.setText("%1.1f km %s\n%s".format(dist(0)/1000.0, getBearing(dist(1)), age))
    view.findViewById(R.id.station_symbol).asInstanceOf[SymbolView].setSymbol(symbol)

    val reportText = if (isWeatherMessage(comment)) parseWXReport(comment) else comment
    view.findViewById(R.id.listmessage).asInstanceOf[TextView].setText(reportText)

    super.bindView(view, context, cursor)
  }

  def getNeighborFilter() = new FilterQueryProvider() {
    def runQuery(constraint : CharSequence) = {
      if (constraint.length() > 0)
        storage.getNeighborsLike("%s%%".format(constraint),
          my_lat, my_lon, System.currentTimeMillis - prefs.getShowAge(), "300")
      else
        storage.getNeighbors(mycall, my_lat, my_lon,
          System.currentTimeMillis - prefs.getShowAge(), "300")
    }
  }

  def load_cursor(i : Intent) = {
    import StationListAdapter._
    val cursor = storage.getStaPosition(mycall)
    if (cursor.getCount() > 0) {
      cursor.moveToFirst()
      my_lat = cursor.getInt(StorageDatabase.Station.COLUMN_LAT)
      my_lon = cursor.getInt(StorageDatabase.Station.COLUMN_LON)
    }
    cursor.close()
    val c = mode match {
      case SINGLE => storage.getStaPosition(targetcall)
      case NEIGHBORS => storage.getNeighbors(mycall, my_lat, my_lon,
        System.currentTimeMillis - prefs.getShowAge(), "300")
      case SSIDS => storage.getAllSsids(targetcall)
    }
    c.getCount()
    c
  }

  def replace_cursor(c : Cursor) {
    if (!context.asInstanceOf[ListActivity].getListView().hasTextFilter())
      changeCursor(c)
    context.asInstanceOf[LoadingIndicator].onStopLoading()
  }
  def cancel_cursor(c : Cursor) {
    c.close()
  }

  def reload() {
    locReceiver.startTask(null)
  }

  def onDestroy() {
    context.unregisterReceiver(locReceiver)
    changeCursor(null)
  }
}
