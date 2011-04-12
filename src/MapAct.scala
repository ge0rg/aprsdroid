package org.aprsdroid.app

import _root_.android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import _root_.android.graphics.drawable.{Drawable, BitmapDrawable}
import _root_.android.graphics.{Canvas, Paint, Path, Point, Rect, Typeface}
import _root_.android.os.{Bundle, Handler}
import _root_.android.util.Log
import _root_.android.view.{Menu, MenuItem, View}
import _root_.android.widget.TextView
import _root_.com.google.android.maps._
import _root_.scala.collection.mutable.ArrayBuffer
import _root_.java.util.ArrayList

// to make scala-style iterating over arraylist possible
import scala.collection.JavaConversions._

class MapAct extends MapActivity {
	val TAG = "MapAct"

	lazy val prefs = new PrefsWrapper(this)
	lazy val uihelper = new UIHelper(this, R.id.map, prefs)
	lazy val mapview = findViewById(R.id.mapview).asInstanceOf[MapView]
	lazy val allicons = this.getResources().getDrawable(R.drawable.allicons)
	lazy val db = StorageDatabase.open(this)
	lazy val staoverlay = new StationOverlay(allicons, this, db)
	lazy val loading = findViewById(R.id.loading).asInstanceOf[TextView]

	var showObjects = false

	lazy val locReceiver = new LocationReceiver2[ArrayList[Station]](staoverlay.load_stations,
			staoverlay.replace_stations, staoverlay.cancel_stations)

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.mapview)
		mapview.setBuiltInZoomControls(true)

		locReceiver.startTask(null)
		mapview.getOverlays().add(staoverlay)

		// listen for new positions
		registerReceiver(locReceiver, new IntentFilter(AprsService.UPDATE))

	}

	override def onDestroy() {
		super.onDestroy()
		unregisterReceiver(locReceiver)
	}
	override def isRouteDisplayed() = false

	override def onCreateOptionsMenu(menu : Menu) : Boolean = {
		getMenuInflater().inflate(R.menu.options_map, menu);
		true
	}
	override def onPrepareOptionsMenu(menu : Menu) = uihelper.onPrepareOptionsMenu(menu)

	override def onOptionsItemSelected(mi : MenuItem) : Boolean = {
		mi.getItemId match {
		case R.id.objects =>
			mi.setChecked(!mi.isChecked())
			showObjects = mi.isChecked()
			loading.setVisibility(View.VISIBLE)
			locReceiver.startTask(null)
			mapview.invalidate()
			true
		case R.id.satellite =>
			mi.setChecked(!mi.isChecked())
			mapview.setSatellite(mi.isChecked())
			true
		case _ => uihelper.optionsItemAction(mi)
		}
	}

	def animateToCall() {
		val i = getIntent()
		if (i != null && i.getStringExtra("call") != null) {
			val targetcall = i.getStringExtra("call")
			val cursor = db.getStaPositions(targetcall, "1")
			if (cursor.getCount() > 0) {
				cursor.moveToFirst()
				val lat = cursor.getInt(StorageDatabase.Position.COLUMN_LAT)
				val lon = cursor.getInt(StorageDatabase.Position.COLUMN_LON)
				mapview.getController().animateTo(new GeoPoint(lat, lon))
			}
			cursor.close()
			
		}

	}

	def onPostLoad() {
		loading.setVisibility(View.GONE)
		mapview.invalidate()
		animateToCall()
	}
}

class Station(val movelog : ArrayBuffer[GeoPoint], val point : GeoPoint,
	val call : String, val message : String, val symbol : String)
	extends OverlayItem(point, call, message) {

}

class StationOverlay(icons : Drawable, context : MapAct, db : StorageDatabase) extends ItemizedOverlay[Station](icons) {
	val TAG = "StationOverlay"

	//lazy val calls = new scala.collection.mutable.HashMap[String, Boolean]()
	var stations = new java.util.ArrayList[Station]()

	// prevent android bug #11666
	populate()

	lazy val symbolSize = (context.getResources().getDisplayMetrics().density * 16).toInt

	override def size() = stations.size()
	override def createItem(idx : Int) : Station = stations.get(idx)

	def symbol2rect(symbol : String) : Rect = {
		val alt_offset = if (symbol(0) == '/') 0 else symbolSize*6
		val index = symbol(1) - 32
		val x = (index / 16) * symbolSize + alt_offset
		val y = (index % 16) * symbolSize
		new Rect(x, y, x+symbolSize, y+symbolSize)
	}

	def symbolIsOverlayed(symbol : String) = {
		(symbol(0) != '/' && symbol(0) != '\\')
	}

	def drawTrace(c : Canvas, m : MapView, s : Station) : Unit = {
		//Log.d(TAG, "drawing trace of %s".format(call))

		val tracePaint = new Paint()
		tracePaint.setARGB(200, 255, 128, 128)
		tracePaint.setStyle(Paint.Style.STROKE)
		tracePaint.setStrokeJoin(Paint.Join.ROUND)
		tracePaint.setStrokeCap(Paint.Cap.ROUND)
		tracePaint.setStrokeWidth(2)
		tracePaint.setAntiAlias(true)


		val path = new Path()
		val point = new Point()

		if (s.movelog.size() < 2) {
			return
		}
		var first = true
		for (p <- s.movelog) {
			m.getProjection().toPixels(p, point)
			if (first) {
				path.moveTo(point.x, point.y)
				first = false
			} else
				path.lineTo(point.x, point.y)
		}
		c.drawPath(path, tracePaint)
	}

	override def draw(c : Canvas, m : MapView, shadow : Boolean) : Unit = {
		if (shadow) return;
		Benchmark("draw") {

		val fontSize = symbolSize*3/4
		val textPaint = new Paint()
		textPaint.setARGB(255, 200, 255, 200)
		textPaint.setTextAlign(Paint.Align.CENTER)
		textPaint.setTextSize(fontSize)
		textPaint.setTypeface(Typeface.MONOSPACE)
		textPaint.setAntiAlias(true)

		val symbPaint = new Paint(textPaint)
		symbPaint.setARGB(255, 255, 255, 255)
		symbPaint.setTextSize(fontSize - 1)

		val strokePaint = new Paint(textPaint)
		strokePaint.setARGB(255, 0, 0, 0)
		strokePaint.setStyle(Paint.Style.STROKE)
		strokePaint.setStrokeWidth(2)

		val symbStrPaint = new Paint(strokePaint)
		symbStrPaint.setTextSize(fontSize - 1)

		val iconbitmap = icons.asInstanceOf[BitmapDrawable].getBitmap

		val p = new Point()
		val proj = m.getProjection()
		val zoom = m.getZoomLevel()
		val (width, height) = (m.getWidth(), m.getHeight())
		val ss = symbolSize/2
		for (s <- stations) {
			proj.toPixels(s.point, p)
			if (p.x >= 0 && p.y >= 0 && p.x < width && p.y < height) {
				val srcRect = symbol2rect(s.symbol)
				val destRect = new Rect(p.x-ss, p.y-ss, p.x+ss, p.y+ss)
				// first draw callsign and trace
				if (zoom >= 10) {
					drawTrace(c, m, s)

					c.drawText(s.call, p.x, p.y+ss+fontSize, strokePaint)
					c.drawText(s.call, p.x, p.y+ss+fontSize, textPaint)
				}
				// then the bitmap
				c.drawBitmap(iconbitmap, srcRect, destRect, null)
				// and finally the bitmap overlay, if any
				if (zoom >= 6 && symbolIsOverlayed(s.symbol)) {
					c.drawText(s.symbol(0).toString(), p.x, p.y+ss/2, symbStrPaint)
					c.drawText(s.symbol(0).toString(), p.x, p.y+ss/2, symbPaint)
				}
			}
		}
		}
	}

	def addStation(sta : Station) {
		//if (calls.contains(sta.getTitle()))
		//	return
		//calls.add(sta.getTitle(), true)
		stations.add(sta)
	}

	override def onTap(index : Int) : Boolean = {
		val s = stations(index)
		Log.d(TAG, "user clicked on " + s.call)
		context.startActivity(new Intent(context, classOf[StationActivity]).putExtra("call", s.call));
		true
	}

	def load_stations(i : Intent) : ArrayList[Station] = {
		val s = new ArrayList[Station]()
		val filter = if (context.showObjects) null else "ORIGIN IS NULL"
		val c = db.getPositions(filter, null, null)
		c.moveToFirst()
		var m = new ArrayBuffer[GeoPoint]()
		while (!c.isAfterLast()) {
			val call = c.getString(StorageDatabase.Position.COLUMN_CALL)
			val lat = c.getInt(StorageDatabase.Position.COLUMN_LAT)
			val lon = c.getInt(StorageDatabase.Position.COLUMN_LON)
			val symbol = c.getString(StorageDatabase.Position.COLUMN_SYMBOL)
			val comment = c.getString(StorageDatabase.Position.COLUMN_COMMENT)
			val p = new GeoPoint(lat, lon)
			m.add(p)
			// peek at the next row
			c.moveToNext()
			val next_call = if (!c.isAfterLast()) c.getString(StorageDatabase.Position.COLUMN_CALL) else null
			c.moveToPrevious()
			if (next_call != call) {
				//Log.d(TAG, "end of call: " + call + " " + next_call + " " + m.size())
				s.add(new Station(m, p, call, comment, symbol))
				m = new ArrayBuffer[GeoPoint]()
			}
			c.moveToNext()
		}
		c.close()
		Log.d(TAG, "total %d items".format(s.size()))
		s
	}

	def replace_stations(s : ArrayList[Station]) {
		stations = s
		setLastFocusedIndex(-1)
		Benchmark("populate") { populate() }
		context.onPostLoad()
	}
	def cancel_stations(s : ArrayList[Station]) {
	}
}
