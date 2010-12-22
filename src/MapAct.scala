package de.duenndns.aprsdroid

import _root_.android.app.AlertDialog
import _root_.android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import _root_.android.graphics.drawable.{Drawable, BitmapDrawable}
import _root_.android.graphics.{Canvas, Paint, Path, Point, Rect, Typeface}
import _root_.android.os.{Bundle, Handler}
import _root_.android.util.Log
import _root_.android.view.{LayoutInflater, Menu, MenuItem, View}
import _root_.com.google.android.maps._

// to make scala-style iterating over arraylist possible
import scala.collection.JavaConversions._

class MapAct extends MapActivity {
	val TAG = "MapAct"

	lazy val mapview = findViewById(R.id.mapview).asInstanceOf[MapView]
	lazy val allicons = this.getResources().getDrawable(R.drawable.allicons)
	lazy val db = StorageDatabase.open(this)
	lazy val staoverlay = new StationOverlay(allicons, this, db)

	lazy val locReceiver = new LocationReceiver(new Handler(), () => {
			Benchmark("loadDb") {
				staoverlay.loadDb()
			}
			mapview.invalidate()
			//postlist.setSelection(0)
		})

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.mapview)
		mapview.setBuiltInZoomControls(true)

		staoverlay.loadDb()
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
	override def onPrepareOptionsMenu(menu : Menu) : Boolean = {
		val mi = menu.findItem(R.id.startstopbtn)
		mi.setTitle(if (AprsService.running) R.string.stoplog else R.string.startlog)
		mi.setIcon(if (AprsService.running) android.R.drawable.ic_menu_close_clear_cancel  else android.R.drawable.ic_menu_compass)
		true
	}

	override def onOptionsItemSelected(mi : MenuItem) : Boolean = {
		mi.getItemId match {
		case R.id.preferences =>
			startActivity(new Intent(this, classOf[PrefsAct]));
			true
		case R.id.map =>
			finish();
			true
		case R.id.startstopbtn =>
			val is_running = AprsService.running
			if (!is_running) {
				startService(AprsService.intent(this, AprsService.SERVICE))
			} else {
				stopService(AprsService.intent(this, AprsService.SERVICE))
			}
			true
		case R.id.quit =>
			stopService(AprsService.intent(this, AprsService.SERVICE))
			finish();
			true
		case _ => false
		}
	}

}

class Station(val point : GeoPoint, val call : String, val message : String, val symbol : String)
	extends OverlayItem(point, call, message) {


}

class StationOverlay(icons : Drawable, context : Context, db : StorageDatabase) extends ItemizedOverlay[Station](icons) {
	val TAG = "StationOverlay"

	//lazy val calls = new scala.collection.mutable.HashMap[String, Boolean]()
	lazy val stations = new java.util.ArrayList[Station]()

	override def size() = stations.size()
	override def createItem(idx : Int) : Station = stations.get(idx)

	def symbol2rect(symbol : String) : Rect = {
		val alt_offset = if (symbol(0) == '/') 0 else 96
		val index = symbol(1) - 32
		val x = (index / 16) * 16 + alt_offset
		val y = (index % 16) * 16
		new Rect(x, y, x+16, y+16)
	}

	def symbolIsOverlayed(symbol : String) = {
		(symbol(0) != '/' && symbol(0) != '\\')
	}

	def drawTrace(c : Canvas, m : MapView, call : String) : Unit = {
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

		val cur = db.getStaPositions(call, "%d".format(System.currentTimeMillis() - 30*3600*1000))
		if (cur.getCount() < 2) {
			cur.close()
			return
		}
		cur.moveToFirst()
		var first = true
		while (!cur.isAfterLast()) {
			val lat = cur.getInt(cur.getColumnIndexOrThrow(StorageDatabase.Position.LAT))
			val lon = cur.getInt(cur.getColumnIndexOrThrow(StorageDatabase.Position.LON))
			m.getProjection().toPixels(new GeoPoint(lat, lon), point)
			if (first) {
				path.moveTo(point.x, point.y)
				first = false
			} else
				path.lineTo(point.x, point.y)
			cur.moveToNext()
		}
		cur.close()
		c.drawPath(path, tracePaint)
	}

	override def draw(c : Canvas, m : MapView, shadow : Boolean) : Unit = {
		if (shadow) return;
		Benchmark("draw") {

		val textPaint = new Paint()
		textPaint.setARGB(255, 200, 255, 200)
		textPaint.setTextAlign(Paint.Align.CENTER)
		textPaint.setTextSize(12)
		textPaint.setTypeface(Typeface.MONOSPACE)
		textPaint.setAntiAlias(true)

		val symbPaint = new Paint(textPaint)
		symbPaint.setARGB(255, 255, 255, 255)
		symbPaint.setTextSize(11)

		val strokePaint = new Paint(textPaint)
		strokePaint.setARGB(255, 0, 0, 0)
		strokePaint.setStyle(Paint.Style.STROKE)
		strokePaint.setStrokeWidth(2)

		val symbStrPaint = new Paint(strokePaint)
		symbStrPaint.setTextSize(11)

		val iconbitmap = icons.asInstanceOf[BitmapDrawable].getBitmap

		val p = new Point()
		for (s <- stations) {
			m.getProjection().toPixels(s.point, p)
			if (p.x >= 0 && p.y >= 0 && p.x < m.getWidth() && p.y < m.getHeight()) {
				val srcRect = symbol2rect(s.symbol)
				val destRect = new Rect(p.x-8, p.y-8, p.x+8, p.y+8)
				// first draw callsign and trace
				if (m.getZoomLevel() >= 10) {
					drawTrace(c, m, s.call)

					c.drawText(s.call, p.x, p.y+20, strokePaint)
					c.drawText(s.call, p.x, p.y+20, textPaint)
				}
				// then the bitmap
				c.drawBitmap(iconbitmap, srcRect, destRect, null)
				// and finally the bitmap overlay, if any
				if (m.getZoomLevel() >= 6 && symbolIsOverlayed(s.symbol)) {
					c.drawText(s.symbol(0).toString(), p.x, p.y+4, symbStrPaint)
					c.drawText(s.symbol(0).toString(), p.x, p.y+4, symbPaint)
				}
			}
		}
		}
	}

	def loadDb() {
		stations.clear()
		val c = db.getPositions(null, null, null)
		c.moveToFirst()
		while (!c.isAfterLast()) {
			val call = c.getString(StorageDatabase.Position.COLUMN_CALL)
			val symbol = c.getString(StorageDatabase.Position.COLUMN_SYMBOL)
			val comment = c.getString(StorageDatabase.Position.COLUMN_COMMENT)
			val lat = c.getInt(StorageDatabase.Position.COLUMN_LAT)
			val lon = c.getInt(StorageDatabase.Position.COLUMN_LON)
			addStation(new Station(new GeoPoint(lat, lon), call, comment, symbol))
			c.moveToNext()
		}
		c.close()
		setLastFocusedIndex(-1)
		populate()
		Log.d(TAG, "total %d items".format(size()))
	}

	def addStation(sta : Station) {
		//if (calls.contains(sta.getTitle()))
		//	return
		//calls.add(sta.getTitle(), true)
		stations.add(sta)
	}

	def addStation(post : String) {
		try {
			val (call, lat, lon, sym, comment, origin) = AprsPacket.parseReport(post)
			Log.d(TAG, "got %s(%d, %d)%s -> %s".format(call, lat, lon, sym, comment))
			addStation(new Station(new GeoPoint(lat, lon), call, comment, sym))
		} catch {
		case e : Exception =>
			Log.d(TAG, "bad " + post)
		}
	}

	override def onTap(index : Int) : Boolean = {
		val s = stations(index)
		Log.d(TAG, "user clicked on " + s.call)

		// extract stations last report from database
		var cur = db.getPosts("MESSAGE like ?", Array("%s%%".format(s.call)), "1")
		cur.moveToFirst()
		val message = if (!cur.isAfterLast()) {
				"%s %s".format(cur.getString(cur.getColumnIndexOrThrow("TSS")),
					cur.getString(StorageDatabase.Post.COLUMN_MESSAGE))
			} else {
				// fall back to positions table
				"%s> %s".format(s.call, s.message)
			}
		cur.close()
		// display a dialog with last report
		val title = context.getString(R.string.sta_lastreport, s.call)

		val ssidlist = new scala.collection.mutable.ArrayBuffer[CharSequence]()
		cur = db.getAllSsids(s.call)
		cur.moveToFirst()
		while  (!cur.isAfterLast()) {
			ssidlist += cur.getString(StorageDatabase.Position.COLUMN_CALL)
			Log.d(TAG, "%s has %s".format(s.call, cur.getString(StorageDatabase.Position.COLUMN_CALL)))
			cur.moveToNext()
		}
		cur.close()
		val qrzurl = "http://qrz.com/db/%s".format(s.call.split("-")(0))
		new AlertDialog.Builder(context).setTitle(title)
			.setMessage(message)
			//.setItems(ssidlist.toArray, null)
			.setPositiveButton("QRZ.com", new UrlOpener(context, qrzurl))
			.setNegativeButton(android.R.string.ok, null)
			.create.show

		true
	}
}
