package org.aprsdroid.app

import _root_.android.Manifest
import _root_.android.app.AlertDialog
import _root_.android.content.{DialogInterface, Intent, IntentFilter}
import _root_.android.content.pm.PackageManager
import _root_.android.content.res.Configuration
import _root_.android.database.Cursor
import _root_.android.graphics.drawable.{BitmapDrawable, Drawable}
import _root_.android.graphics.{Canvas, Paint, Path, Point, Rect, Typeface}
import _root_.android.os.{Build, Bundle}
import _root_.android.util.Log
import _root_.android.view.{KeyEvent, Menu, MenuItem, View}
import _root_.android.widget.Toast
import _root_.org.mapsforge.v3.android.maps._
import _root_.org.mapsforge.v3.core.{GeoPoint, Tile}
import _root_.org.mapsforge.v3.android.maps.overlay.{ItemizedOverlay, OverlayItem}

import _root_.scala.collection.mutable.ArrayBuffer
import _root_.java.io.File
import _root_.java.util.ArrayList

import org.mapsforge.v3.android.maps.mapgenerator.{MapGeneratorFactory, MapGeneratorInternal}

// to make scala-style iterating over arraylist possible
import scala.collection.JavaConversions._

class MapAct extends MapActivity with MapMenuHelper {
	override val TAG = "APRSdroid.Map"

	menu_id = R.id.map

	lazy val mapview = findViewById(R.id.mapview).asInstanceOf[MapView]
	lazy val allicons = this.getResources().getDrawable(R.drawable.allicons)
	lazy val db = StorageDatabase.open(this)
	lazy val staoverlay = new StationOverlay(allicons, this, db)
	lazy val loading = findViewById(R.id.loading).asInstanceOf[View]
	lazy val locReceiver = new LocationReceiver2[ArrayList[Station]](staoverlay.load_stations,
			staoverlay.replace_stations, staoverlay.cancel_stations)

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.mapview)
		mapview.setBuiltInZoomControls(true)
		mapview.getOverlays().add(staoverlay)
		mapview.setTextScale(getResources().getDisplayMetrics().density)

		startLoading()
	}

	override def onResume() {
		super.onResume()
		// only make it default if not tracking
		if (targetcall == "")
			makeLaunchActivity("map")
		else
			setLongTitle(R.string.app_map, targetcall)
		setKeepScreenOn()
		setVolumeControls()
		checkPermissions(Array(Manifest.permission.WRITE_EXTERNAL_STORAGE), RELOAD_MAP)
		mapview.requestFocus()
	}

	override def onConfigurationChanged(c : Configuration) = {
		super.onConfigurationChanged(c)
		if (targetcall != "")
			setLongTitle(R.string.app_map, targetcall)
	}

	override def onPause() {
		super.onPause()
		val pos = mapview.getMapPosition().getMapPosition()
		if (pos == null || pos.geoPoint == null)
			return
		saveMapViewPosition(pos.geoPoint.latitudeE6/1000000.0f, pos.geoPoint.longitudeE6/1000000.0f, pos.zoomLevel)
	}

	override def onDestroy() {
		super.onDestroy()
		unregisterReceiver(locReceiver)
	}

        override def loadMapViewPosition(lat : Float, lon : Float, zoom : Float) {
		mapview.getController().setCenter(new GeoPoint(lat, lon))
		mapview.getController().setZoom(zoom.asInstanceOf[Int])
        }

	def startLoading() {
		registerReceiver(locReceiver, new IntentFilter(AprsService.UPDATE))
		locReceiver.startTask(null)
	}

	val RELOAD_MAP = 1010

	override def getActionName(action : Int): Int = {
		action match {
		case RELOAD_MAP => R.string.show_map
		case _ => super.getActionName(action)
		}
	}
	override def onAllPermissionsGranted(action: Int): Unit = {
		action match {
		case RELOAD_MAP => reloadMapAndTheme()
		case _ => super.onAllPermissionsGranted(action)
		}
	}

	override def onPermissionsFailed(action: Int, permissions: Set[String]): Unit = {
		if (action == RELOAD_MAP) {
			if (targetcall == "")
				startActivity(new Intent(this, classOf[HubActivity]).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
			finish()
		}
		super.onPermissionsFailed(action, permissions)
	}

	def reloadMapAndTheme() {
		val mapfile = new File(prefs.getString("mapfile", android.os.Environment.getExternalStorageDirectory() + "/aprsdroid.map"))
		if (mapfile.exists() && mapfile.canRead())
			mapview.setMapFile(mapfile)
		else {
			if (prefs.getString("mapfile", null) != null)
				Toast.makeText(this, getString(R.string.mapfile_error, mapfile), Toast.LENGTH_SHORT).show()
			val map_source = MapGeneratorInternal.MAPNIK
			val map_gen = new OsmTileDownloader()
			map_gen.setUserAgent(getString(R.string.build_version))
			mapview.setMapGenerator(map_gen)
		}
		val themefile = new File(prefs.getString("themefile", android.os.Environment.getExternalStorageDirectory() + "/aprsdroid.xml"))
		if (themefile.exists())
			mapview.setRenderTheme(themefile)
		loadMapViewPosition()
	}

	override def onKeyDown(keyCode : Int, event : KeyEvent) : Boolean = {
		keyCode match {
		case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD |
		     KeyEvent.KEYCODE_MEDIA_NEXT =>
			changeZoom(+1)
			true
		case KeyEvent.KEYCODE_MEDIA_REWIND |
		     KeyEvent.KEYCODE_MEDIA_PREVIOUS =>
			changeZoom(-1)
			true
		case KeyEvent.KEYCODE_MEDIA_PLAY |
		     KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE =>
			if (mapview.hasFocus())
				mapview.focusSearch(View.FOCUS_FORWARD).requestFocus()
			else
				mapview.requestFocus()
			true
		case KeyEvent.KEYCODE_DPAD_CENTER |
		     KeyEvent.KEYCODE_ENTER =>
			// TODO: return coordinates
			if (isCoordinateChooser) {
				finish()
			}
			true
		case _ => super.onKeyDown(keyCode, event)
		}
	}

	def updateCoordinateInfo(): Unit = {
		if (!isCoordinateChooser)
			return
		val pos = mapview.getMapPosition().getMapPosition()
		if (pos == null || pos.geoPoint == null)
			return
		updateCoordinateInfo(pos.geoPoint.latitudeE6/1000000.0f, pos.geoPoint.longitudeE6/1000000.0f)
	}

	override def changeZoom(delta : Int) {
		mapview.getController().setZoom(mapview.getMapPosition().getZoomLevel() + delta)
	}

	def animateToCall() {
		if (targetcall != "") {
			val (found, lat, lon) = getStaPosition(db, targetcall)
			if (found)
				mapview.getController().setCenter(new GeoPoint(lat, lon))
		}
	}

	def onPostLoad() {
		mapview.invalidate()
		onStopLoading()
		animateToCall()
	}

	override def reloadMap() {
		onStartLoading()
		locReceiver.startTask(null)
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
		val lat_ok = (bl.latitudeE6 <= pt.latitudeE6 && pt.latitudeE6 <= tr.latitudeE6)
		val lon_ok = if (bl.longitudeE6 <= tr.longitudeE6)
				     (bl.longitudeE6 <= pt.longitudeE6 && pt.longitudeE6 <= tr.longitudeE6)
			     else
				     (bl.longitudeE6 <= pt.longitudeE6 || pt.longitudeE6 <= tr.longitudeE6)
		lat_ok && lon_ok
	}
}

class StationOverlay(icons : Drawable, context : MapAct, db : StorageDatabase) extends ItemizedOverlay[Station](icons) {
	val TAG = "APRSdroid.StaOverlay"

	//lazy val calls = new scala.collection.mutable.HashMap[String, Boolean]()
	var stations = new java.util.ArrayList[Station]()

	// prevent android bug #11666
	populate()

	val iconbitmap = icons.asInstanceOf[BitmapDrawable].getBitmap
	val symbolSize = iconbitmap.getWidth()/16
	lazy val drawSize = (context.getResources().getDisplayMetrics().density * 24).toInt

	icons.setBounds(0, 0, symbolSize, symbolSize)

	override def size() = stations.size()
	override def createItem(idx : Int) : Station = stations.get(idx)

	def symbol2rect(index : Int, page : Int) : Rect = {
		// check for overflow
		if (index < 0 || index >= 6*16)
			return new Rect(0, 0, symbolSize, symbolSize)
		val alt_offset = page*symbolSize*6
		val y = (index / 16) * symbolSize + alt_offset
		val x = (index % 16) * symbolSize
		new Rect(x, y, x+symbolSize, y+symbolSize)
	}
	def symbol2rect(symbol : String) : Rect = {
		symbol2rect(symbol(1) - 33, if (symbol(0) == '/') 0 else 1)
	}

	def symbolIsOverlayed(symbol : String) = {
		(symbol(0) != '/' && symbol(0) != '\\')
	}

	def drawTrace(c : Canvas, proj : Projection, s : Station) : Unit = {
		//Log.d(TAG, "drawing trace of %s".format(call))

		val tracePaint = new Paint()
		tracePaint.setARGB(128, 100, 100, 255)
		tracePaint.setStyle(Paint.Style.STROKE)
		tracePaint.setStrokeJoin(Paint.Join.ROUND)
		tracePaint.setStrokeCap(Paint.Cap.ROUND)
		tracePaint.setStrokeWidth(drawSize/6)
		tracePaint.setAntiAlias(true)

		val dotPaint = new Paint()
		dotPaint.setARGB(128, 255, 0, 0)
		dotPaint.setStyle(Paint.Style.FILL)
		dotPaint.setAntiAlias(true)


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
			c.drawCircle(point.x, point.y, drawSize/12, dotPaint)
		}
		c.drawPath(path, tracePaint)
	}

	override def drawOverlayBitmap(c : Canvas, dp : Point, proj : Projection, zoom : Byte) : Unit = {

		if (!context.mapview.getMapPosition.isValid)
			return
		Log.d(TAG, "draw: symbolSize=" + symbolSize + " drawSize=" + drawSize)
		val fontSize = drawSize*7/8
		val textPaint = new Paint()
		textPaint.setColor(0xff000000)
		textPaint.setTextAlign(Paint.Align.CENTER)
		textPaint.setTextSize(fontSize)
		textPaint.setTypeface(Typeface.MONOSPACE)
		textPaint.setAntiAlias(true)

		val symbPaint = new Paint(textPaint)
		symbPaint.setARGB(255, 255, 255, 255)
		symbPaint.setTextSize(drawSize*3/4 - 1)

		val strokePaint = new Paint(textPaint)
		strokePaint.setColor(0xffc8ffc8)
		strokePaint.setStyle(Paint.Style.STROKE)
		strokePaint.setStrokeWidth(drawSize.asInstanceOf[Float]/12.0f)

		strokePaint.setShadowLayer(10, 0, 0, 0x80c8ffc8)


		val p = new Point()
		val (width, height) = (c.getWidth(), c.getHeight())
		val ss = drawSize/2
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
				if (symbolIsOverlayed(s.symbol)) {
					// use page 2, overlay letters
					c.drawBitmap(iconbitmap, symbol2rect(s.symbol(0)-33, 2), destRect, null)
				}
			}
		}
		import AprsService.block2runnable
		context.handler.post { context.updateCoordinateInfo() }
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
		Benchmark("populate") { populate() }
		context.onPostLoad()
	}
	def cancel_stations(s : ArrayList[Station]) {
	}

}
