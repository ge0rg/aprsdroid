package org.aprsdroid.app

import _root_.android.app.AlertDialog
import _root_.android.content.{BroadcastReceiver, Context, DialogInterface, Intent, IntentFilter}
import _root_.android.database.Cursor
import _root_.android.graphics.drawable.{Drawable, BitmapDrawable}
import _root_.android.graphics.{Canvas, Paint, Path, Point, Rect, Typeface}
import _root_.android.os.{Bundle, Handler}
import _root_.android.util.Log
import _root_.android.view.{Menu, MenuItem, View}
import _root_.android.widget.SimpleCursorAdapter
import _root_.android.widget.Spinner
import _root_.android.widget.TextView
import _root_.com.google.android.maps._
import _root_.scala.collection.mutable.ArrayBuffer
import _root_.java.util.ArrayList

// to make scala-style iterating over arraylist possible
import scala.collection.JavaConversions._

class MapAct extends MapActivity with UIHelper {
	val TAG = "APRSdroid.Map"

	menu_id = R.id.map
	lazy val mapview = findViewById(R.id.mapview).asInstanceOf[MapView]
	lazy val allicons = this.getResources().getDrawable(R.drawable.allicons)
	lazy val db = StorageDatabase.open(this)
	lazy val staoverlay = new StationOverlay(allicons, this, db)
	lazy val loading = findViewById(R.id.loading)
	lazy val targetcall = getTargetCall()

	var showObjects = false

	lazy val locReceiver = new LocationReceiver2[ArrayList[Station]](staoverlay.load_stations,
			staoverlay.replace_stations, staoverlay.cancel_stations)

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.mapview)
		mapview.setBuiltInZoomControls(true)

		locReceiver.startTask(null)
		showObjects = prefs.getShowObjects()
		mapview.setSatellite(prefs.getShowSatellite())
		mapview.getOverlays().add(staoverlay)

		// listen for new positions
		registerReceiver(locReceiver, new IntentFilter(AprsService.UPDATE))

	}
	override def onResume() {
		super.onResume()
		// only make it default if not tracking
		if (targetcall == "")
			makeLaunchActivity("map")
		setKeepScreenOn()
		setVolumeControls()
	}

	override def onDestroy() {
		super.onDestroy()
		unregisterReceiver(locReceiver)
	}
	override def isRouteDisplayed() = false

	override def onCreateOptionsMenu(menu : Menu) : Boolean = {
		getMenuInflater().inflate(R.menu.options, menu);
		true
	}

	override def onOptionsItemSelected(mi : MenuItem) : Boolean = {
		mi.getItemId match {
		case R.id.objects =>
			val newState = prefs.toggleBoolean("show_objects", true)
			mi.setChecked(newState)
			showObjects = newState
			onStartLoading()
			locReceiver.startTask(null)
			true
		case R.id.satellite =>
			val newState = prefs.toggleBoolean("show_satellite", false)
			mi.setChecked(newState)
			mapview.setSatellite(newState)
			true
		case _ => super.onOptionsItemSelected(mi)
		}
	}

	def getTargetCall() : String = {
		val i = getIntent()
		if (i != null && i.getDataString() != null) {
			i.getDataString()
		} else ""
	}

	def animateToCall() {
		if (targetcall != "") {
			val cursor = db.getStaPosition(targetcall)
			if (cursor.getCount() > 0) {
				cursor.moveToFirst()
				val lat = cursor.getInt(StorageDatabase.Station.COLUMN_LAT)
				val lon = cursor.getInt(StorageDatabase.Station.COLUMN_LON)
				mapview.getController().animateTo(new GeoPoint(lat, lon))
			}
			cursor.close()
		}
	}

	def onPostLoad() {
		mapview.invalidate()
		onStopLoading()
		animateToCall()
	}

	override def onStartLoading() {
		loading.setVisibility(View.VISIBLE)
	}

	override def onStopLoading() {
		loading.setVisibility(View.GONE)
	}
}

class Station(val movelog : ArrayBuffer[GeoPoint], val pt : GeoPoint,
	val call : String, val origin : String, val symbol : String)
	extends OverlayItem(pt, call, origin) {

	def inArea(bl : GeoPoint, tr : GeoPoint) = {
		val lat_ok = (bl.getLatitudeE6 <= pt.getLatitudeE6 && pt.getLatitudeE6 <= tr.getLatitudeE6)
		val lon_ok = if (bl.getLongitudeE6 <= tr.getLongitudeE6)
				     (bl.getLongitudeE6 <= pt.getLongitudeE6 && pt.getLongitudeE6 <= tr.getLongitudeE6)
			     else
				     (bl.getLongitudeE6 <= pt.getLongitudeE6 || pt.getLongitudeE6 <= tr.getLongitudeE6)
		lat_ok && lon_ok
	}
}

class StationOverlay(icons : Drawable, context : MapAct, db : StorageDatabase) extends ItemizedOverlay[Station](icons) {
	val TAG = "APRSdroid.StaOverlay"

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
		// check for overflow
		if (index < 0 || index >= 6*16)
			return new Rect(0, 0, symbolSize, symbolSize)
		val x = (index / 16) * symbolSize + alt_offset
		val y = (index % 16) * symbolSize
		new Rect(x, y, x+symbolSize, y+symbolSize)
	}

	def symbolIsOverlayed(symbol : String) = {
		(symbol(0) != '/' && symbol(0) != '\\')
	}

	def drawTrace(c : Canvas, proj : Projection, s : Station) : Unit = {
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
			proj.toPixels(p, point)
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

		val fontSize = symbolSize*7/8
		val textPaint = new Paint()
		textPaint.setColor(0xff000000)
		textPaint.setTextAlign(Paint.Align.CENTER)
		textPaint.setTextSize(fontSize)
		textPaint.setTypeface(Typeface.MONOSPACE)
		textPaint.setAntiAlias(true)

		val symbPaint = new Paint(textPaint)
		symbPaint.setARGB(255, 255, 255, 255)
		symbPaint.setTextSize(symbolSize*3/4 - 1)

		val strokePaint = new Paint(textPaint)
		strokePaint.setColor(0xffc8ffc8)
		strokePaint.setStyle(Paint.Style.STROKE)
		strokePaint.setStrokeWidth(2)

		val symbStrPaint = new Paint(strokePaint)
		symbStrPaint.setColor(0xff000000)
		symbStrPaint.setTextSize(symbolSize*3/4 - 1)

		strokePaint.setShadowLayer(2, 0, 0, 0xffc8ffc8)

		val iconbitmap = icons.asInstanceOf[BitmapDrawable].getBitmap

		val p = new Point()
		val proj = m.getProjection()
		val zoom = m.getZoomLevel()
		val (width, height) = (c.getWidth(), c.getHeight())
		val ss = symbolSize/2
		for (s <- stations) {
			proj.toPixels(s.pt, p)
			if (p.x >= 0 && p.y >= 0 && p.x < width && p.y < height) {
				val srcRect = symbol2rect(s.symbol)
				val destRect = new Rect(p.x-ss, p.y-ss, p.x+ss, p.y+ss)
				// first draw callsign and trace
				if (zoom >= 10) {
					drawTrace(c, proj, s)

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

	def addStation(sta : Station) {
		//if (calls.contains(sta.getTitle()))
		//	return
		//calls.add(sta.getTitle(), true)
		stations.add(sta)
	}

	override def onTap(gp : GeoPoint, mv : MapView) : Boolean = {
		//Log.d(TAG, "user tapped " + gp)
		//Log.d(TAG, "icon bounds: " + icons.getBounds())
		// convert geopoint to pixels
		val proj = mv.getProjection()
		val p = proj.toPixels(gp, null)
		// ... to pixel area ... to geo area
		//Log.d(TAG, "coords: " + p)
		val botleft = proj.fromPixels(p.x - 16, p.y + 16)
		val topright = proj.fromPixels(p.x + 16, p.y - 16)
		Log.d(TAG, "from " + botleft + " to " + topright)
		// fetch stations in the tap
		val list = stations.filter(_.inArea(botleft, topright)).map(_.call)
		Log.d(TAG, "found " + list.size() + " stations")
		val result = if (list.size() == 0)
			false // nothing found, do not revert to superclass
		else if (list.size() == 1) {
			// found one entry
			val call = list.get(0)
			Log.d(TAG, "user clicked on " + call)
			context.openDetails(call)
			true
		} else {
			// TODO: replace simple adapter with StationListAdapter for better UI
			new AlertDialog.Builder(context).setTitle(R.string.map_select)
				.setItems(list.toArray.asInstanceOf[Array[CharSequence]], new DialogInterface.OnClickListener() {
					override def onClick(di : DialogInterface, item : Int) {
						context.openDetails(list.get(item))
					}})
				.setNegativeButton(android.R.string.cancel, null)
				.show()
			true
		}
		result
	}

	override def onTap(index : Int) : Boolean = {
		val s = stations(index)
		val target = if (s.origin != null && s.origin != "") s.origin
			else s.call
		Log.d(TAG, "user clicked on " + s.call + "/" + target)
		context.openDetails(s.call)
		true
	}

	def fetchStaPositions(call : String, c : Cursor) : ArrayBuffer[GeoPoint] = {
		import StorageDatabase.Position._
		val m = new ArrayBuffer[GeoPoint]()
		// skip forward to the right callsign
		while (!c.isAfterLast() && c.getString(COLUMN_CALL) < call)
			c.moveToNext()
		// add every matching entry to arraybuffer
		while (!c.isAfterLast() && c.getString(COLUMN_CALL) == call) {
			val lat = c.getInt(COLUMN_LAT)
			val lon = c.getInt(COLUMN_LON)
			m.add(new GeoPoint(lat, lon))
			c.moveToNext()
		}
		m
	}

	def load_stations(i : Intent) : ArrayList[Station] = {
		import StorageDatabase.Station._

		val s = new ArrayList[Station]()
		val age_ts = (System.currentTimeMillis - context.prefs.getShowAge()).toString
		val filter = if (context.showObjects) "TS > ? OR CALL=?" else "(ORIGIN IS NULL AND TS > ?) OR CALL=?"
		val c = db.getStations(filter, Array(age_ts, context.targetcall), null)
		c.moveToFirst()
		val pos_c = db.getAllStaPositions(age_ts)
		pos_c.moveToFirst()
		while (!c.isAfterLast()) {
			val call = c.getString(COLUMN_MAP_CALL)
			val lat = c.getInt(COLUMN_MAP_LAT)
			val lon = c.getInt(COLUMN_MAP_LON)
			val symbol = c.getString(COLUMN_MAP_SYMBOL)
			val origin = c.getString(COLUMN_MAP_ORIGIN)
			val p = new GeoPoint(lat, lon)
			val m = fetchStaPositions(call, pos_c)
			s.add(new Station(m, p, call, origin, symbol))
			c.moveToNext()
		}
		c.close()
		pos_c.close()
		Log.d(TAG, "total %d items".format(s.size()))
		s
	}

	def replace_stations(s : ArrayList[Station]) {
		stations = s
		// work around NullPointerException in ItemizedOverlay
		// http://groups.google.com/group/android-developers/browse_thread/thread/38b11314e34714c3
		setLastFocusedIndex(-1)
		Benchmark("populate") { populate() }
		context.onPostLoad()
	}
	def cancel_stations(s : ArrayList[Station]) {
	}

}
