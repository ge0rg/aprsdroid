package org.aprsdroid.app


import java.util.ArrayList

import android.content.{Intent, IntentFilter}
import android.graphics.{Bitmap, Canvas, Color, Rect}
import android.graphics.drawable.BitmapDrawable
import android.text.TextUtils

import scala.collection.mutable.ArrayBuffer

trait MapLoaderBase extends MapMenuHelper {
    menu_id = R.id.map

    lazy val db = StorageDatabase.open(this)
    lazy val locReceiver = new LocationReceiver2[ArrayList[Station]](load_stations,
        load_finished, null)

    override def onDestroy(): Unit = {
        scala.util.control.Exception.ignoring(classOf[IllegalArgumentException]) {
            unregisterReceiver(locReceiver)
        }
        super.onDestroy()
    }

    def newStation(call : String, origin : String, symbol : String,
                  lat : Double, lon : Double,
                  qrg : String, comment : String, speed : Int, course : Int,
                  movelog : ArrayBuffer[Point]) : Station = {
        new Station(call, origin, symbol, lat, lon, qrg, comment, speed, course, movelog)
    }

        def onStationUpdate(sl : ArrayList[Station])

    def startLoading() {
        locReceiver.startTask(null)
        registerReceiver(locReceiver, new IntentFilter(AprsService.UPDATE))
    }

    def load_stations(i : Intent) = {
        import StorageDatabase.Station._

        val s = new ArrayList[Station]()

        val age_ts = (System.currentTimeMillis - prefs.getShowAge()).toString
        val filter = if (showObjects) "TS > ? OR CALL=?" else "(ORIGIN IS NULL AND TS > ?) OR CALL=?"
        val c = db.getStations(filter, Array(age_ts, targetcall), null)
        c.moveToFirst()
        while (!c.isAfterLast()) {
            val call = c.getString(COLUMN_MAP_CALL)
            val lat = c.getInt(COLUMN_MAP_LAT)
            val lon = c.getInt(COLUMN_MAP_LON)
            val symbol = c.getString(COLUMN_MAP_SYMBOL)
            val origin = c.getString(COLUMN_MAP_ORIGIN)
            val qrg = c.getString(COLUMN_MAP_QRG)
            val comment = c.getString(COLUMN_MAP_COMMENT)
            val speed = c.getInt(COLUMN_MAP_SPEED)
            val cse = c.getInt(COLUMN_MAP_CSE)

            if (call != null && !call.isEmpty)
                s.add(newStation(call, origin, symbol, lat/1000000.0d, lon/1000000.0d,
                    qrg, comment, speed, cse,
                    null))
            c.moveToNext()
        }
        c.close()
        s
    }

    override def reloadMap() {
	onStartLoading()
	locReceiver.startTask(null)
    }

    def load_finished(sl: ArrayList[Station]) : Unit = {
        onStationUpdate(sl)
        onStopLoading()
    }

    class Point(val lat : Double, val lon : Double) {}

    class Station(val call : String, val origin : String, val symbol : String,
                  val lat : Double, val lon : Double,
                  val qrg : String, val comment : String, val speed : Int, val course : Int,
                  val movelog : ArrayBuffer[Point]) {

        def callQrg() : String = {
            if (!TextUtils.isEmpty(qrg)) { "%s %s".format(call, qrg) } else call;
        }
    }

    lazy val allicons = this.getResources().getDrawable(R.drawable.allicons)
    lazy val alliconsbitmap = allicons.asInstanceOf[BitmapDrawable].getBitmap
    lazy val symbolSize = alliconsbitmap.getWidth()/16
    lazy val zerorect = new Rect(0, 0, symbolSize, symbolSize)

    def validateSymbol(symbol : String) : String = {
        val index = symbol(1) - 33
        val overlay = if (symbol(0) == '/') 0 else 1
        if (symbol(1) >= '!' && symbol(1) <= '~' &&
            "\\/0123456789ABCDEFGHIJKLMNOPQRSTUVWXY".contains(symbol(0)))
            return symbol
        else return "/." // invalid symbol --> Red X

    }
    def symbol2rect(index : Int, page : Int) : Rect = {
        // check for overflow
        if (index < 0 || index >= 6*16)
            return zerorect
        val y = (index / 16 + 6*page) * symbolSize
        val x = (index % 16) * symbolSize
        new Rect(x, y, x+symbolSize, y+symbolSize)
    }
    def symbol2rect(symbol : String) : Rect = {
        symbol2rect(symbol(1) - 33, if (symbol(0) == '/') 0 else 1)
    }
    def symbolIsOverlayed(symbol : String) = {
        (symbol(0) != '/' && symbol(0) != '\\')
    }


    def symbol2bitmap(symbol : String, size : Int) : Bitmap = {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        b.eraseColor(Color.TRANSPARENT)
        val c = new Canvas(b)
        val rect = new Rect(0, 0, size, size)
        c.drawBitmap(alliconsbitmap, symbol2rect(symbol), rect, null)
        if (symbolIsOverlayed(symbol)) {
            // draw overlay letter in addition to regular symbol
            c.drawBitmap(alliconsbitmap, symbol2rect(symbol(0)-33, 2), rect, null)
        }
        b
    }


}
