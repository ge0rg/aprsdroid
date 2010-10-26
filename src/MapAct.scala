package de.duenndns.aprsdroid

import _root_.android.graphics.drawable.Drawable
import _root_.android.os.Bundle
import _root_.android.util.Log
import _root_.com.google.android.maps._

class MapAct extends MapActivity {
	val TAG = "MapAct"

	lazy val mapview = findViewById(R.id.mapview).asInstanceOf[MapView]
	lazy val pin = this.getResources().getDrawable(R.drawable.cross)
	lazy val staoverlay = new StationOverlay(pin)

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.mapview)
		mapview.setBuiltInZoomControls(true)
		val w = pin.getIntrinsicWidth()
		val h = pin.getIntrinsicHeight()
		pin.setBounds(-w/2, -h/2, w/2, h/2)
		staoverlay.loadDb(StorageDatabase.open(this))
		mapview.getOverlays().add(staoverlay)

	}

	override def isRouteDisplayed() = false
}

class Station(point : GeoPoint, call : String, message : String)
	extends OverlayItem(point, call, message) {


}

class StationOverlay(pin : Drawable) extends ItemizedOverlay[Station](pin) {

	//lazy val calls = new scala.collection.mutable.HashMap[String, Boolean]()
	lazy val stations = new java.util.ArrayList[Station]()

	override def size() = stations.size()
	override def createItem(idx : Int) : Station = stations.get(idx)

	override def draw(c : Canvas, m : MapView, shadow : Boolean) = {
		populate()
		val p = new Point()
		for (s <- stations) {
			m.getProjection().toPixels(s, p)
			c.drawText(s.)
		}
	}

	def loadDb(db : StorageDatabase) {
		val c = db.getPosts("TYPE = 0 OR TYPE = 3", null, "100")
		c.moveToFirst()
		while (!c.isAfterLast()) {
			val msgidx = c.getColumnIndexOrThrow(StorageDatabase.Post.MESSAGE)
			val message = c.getString(msgidx)
			addStation(message)
			c.moveToNext()
		}
		c.close()
		Log.d("StationOverlay", "total %d items".format(size()))
	}

	def addStation(sta : Station) {
		//if (calls.contains(sta.getTitle()))
		//	return
		//calls.add(sta.getTitle(), true)
		stations.add(sta)
		populate()
	}

	def addStation(post : String) {
		try {
			val (call, lat, lon, sym, comment) = AprsPacket.parseReport(post)
			Log.d("StationOverlay", "got %s(%d, %d)%s -> %s".format(call, lat, lon, sym, comment))
			addStation(new Station(new GeoPoint(lat, lon), call, comment))
		} catch {
		case e : Exception =>
			Log.d("StationOverlay", "bad " + post)
		}
	}
}
