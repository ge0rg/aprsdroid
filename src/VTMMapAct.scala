package org.aprsdroid.app

import _root_.java.io.{File, FileInputStream}
import _root_.java.util.{ArrayList, Collections}
import java.util

import _root_.android.Manifest
import _root_.android.app.{Activity, AlertDialog}
import _root_.android.content.res.Configuration
import _root_.android.content.{DialogInterface, Intent, IntentFilter}
import _root_.android.database.Cursor
import _root_.android.graphics.drawable.{BitmapDrawable, Drawable}
import _root_.android.graphics._
import _root_.android.os.Bundle
import _root_.android.util.Log
import _root_.android.view.{KeyEvent, View}
import _root_.android.widget.Toast
import android.net.Uri
import okhttp3.OkHttpClient
import org.oscim.android.MapView
import org.oscim.android.canvas.AndroidBitmap
import org.oscim.core.GeoPoint
import org.oscim.layers.marker.ItemizedLayer.OnItemGestureListener
import org.oscim.layers.marker.MarkerSymbol.HotspotPlace
import org.oscim.layers.marker.{ItemizedLayer, MarkerInterface, MarkerSymbol}
import org.oscim.layers.tile.bitmap.BitmapTileLayer
import org.oscim.layers.tile.buildings.BuildingLayer
import org.oscim.layers.tile.vector.VectorTileLayer
import org.oscim.layers.tile.vector.labeling.LabelLayer
import org.oscim.theme.VtmThemes
import org.oscim.tiling.source.OkHttpEngine
import org.oscim.tiling.source.bitmap.DefaultSources
import org.oscim.tiling.source.mapfile.MapFileTileSource

import _root_.scala.collection.mutable.ArrayBuffer

// to make scala-style iterating over arraylist possible
import scala.collection.JavaConversions._

class VTMMapAct extends Activity with MapLoaderBase {
	override val TAG = "APRSdroid.VTM"

	menu_id = R.id.vtm

	lazy val mapview = findViewById(R.id.mapview).asInstanceOf[MapView]
	lazy val loading = findViewById(R.id.loading).asInstanceOf[View]
	lazy val iconlayer = initializeIconLayer()

	def initializeIconLayer(): ItemizedLayer = {
		val bitmap = new AndroidBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.icon))
		val defaultsymbol = new MarkerSymbol(bitmap, HotspotPlace.CENTER, false)
		new ItemizedLayer(mapview.map, new util.ArrayList[MarkerInterface](), defaultsymbol, new MapItemGestureListener().asInstanceOf[OnItemGestureListener[MarkerInterface]])
	}
	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.vtmmapview)

		startLoading()
	}

	override def onResume() {
		super.onResume()
		// only make it default if not tracking
		if (isCoordinateChooser)
			setTitle(R.string.p_source_from_map)
		else if (targetcall == "")
			makeLaunchActivity("map")
		else
			setLongTitle(R.string.app_map, targetcall)
		setKeepScreenOn()
		setVolumeControls()
		//checkPermissions(Array(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), RELOAD_MAP)
		reloadMapAndTheme()
		mapview.onResume()
		mapview.requestFocus()
	}

	override def onConfigurationChanged(c : Configuration) = {
		super.onConfigurationChanged(c)
		if (targetcall != "")
			setLongTitle(R.string.app_map, targetcall)
	}

	override def onPause() {
		super.onPause()
		mapview.onPause()
	}

	override def onDestroy() {
		super.onDestroy()
		mapview.map.destroy()
	}

	override def newStation(call : String, origin : String, symbol : String,
								 lat : Double, lon : Double,
								 qrg : String, comment : String, speed : Int, course : Int,
								 movelog : ArrayBuffer[Point]) : super.Station = {
		new this.Station(call, origin, symbol, lat, lon, qrg, comment, speed, course, movelog)
	}

	def onStationUpdate(sl : ArrayList[super.Station]): Unit = {
		iconlayer.removeAllItems()
		iconlayer.getItemList.addAll(sl.asInstanceOf[ArrayList[MarkerInterface]])
		iconlayer.populate()
	}


	override def loadMapViewPosition(lat : Float, lon : Float, zoom : Float): Unit = {
		mapview.map().setMapPosition(lat, lon, zoom)
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

	override def onPermissionsFailedCancel(action: Int): Unit = {
		if (action == RELOAD_MAP) {
			if (targetcall == "")
				startActivity(new Intent(this, classOf[HubActivity]).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
			finish()
		}
		//super.onPermissionsFailed(action, permissions)
	}

	def reloadMapAndTheme() {
		val mapfilename = prefs.getString("mapfile", "file://" + android.os.Environment.getExternalStorageDirectory() + "/aprsdroid.map")
		val mapfile = new File(mapfilename)
		val map = mapview.map
		var error = if (mapfile.exists() && mapfile.canRead()) {
			val tileSource = new MapFileTileSource()
			val fis = getContentResolver.openInputStream(Uri.parse(mapfilename)).asInstanceOf[FileInputStream]
			tileSource.setMapFileInputStream(fis)
			val l = map.setBaseMap(tileSource)
      //map.layers().clear()
			map.layers().add(new BuildingLayer(map, l))
			map.layers().add(new LabelLayer(map, l))
      map.layers().add(iconlayer)
			map.setTheme(VtmThemes.DEFAULT)
			null
		} else if (prefs.getString("mapfile", null) != null) {
			// output generic error if file was configured but is not loadable
			getString(R.string.mapfile_error, mapfile)
		} else {
			// do not output error if no map file was configured, silently load online osm
			""
		}
		if (error != null) {
			if (!error.isEmpty)
				Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
			val builder = new OkHttpClient.Builder()
			val source = DefaultSources.OPENSTREETMAP.build()
			source.setHttpEngine(new OkHttpEngine.OkHttpFactory(builder))
			source.setHttpRequestHeaders(Collections.singletonMap("User-Agent", getString(R.string.build_version)))
      val l = new BitmapTileLayer(map, source)
      map.layers.add(l)
		}
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
				setResult(android.app.Activity.RESULT_OK, resultIntent)
				finish()
			}
			true
		case _ => super.onKeyDown(keyCode, event)
		}
	}

	def updateCoordinateInfo(): Unit = {
		if (!isCoordinateChooser)
			return
	}

	override def changeZoom(delta : Int): Unit = {
		val mp  = mapview.map.getMapPosition()
		mp.scale = mp.scale + delta
    mapview.map.setMapPosition(mp)
	}

	def animateToCall() {
		if (targetcall != "") {
			val (found, lat, lon) = getStaPosition(db, targetcall)
      if (found)
				mapview.map().setMapPosition(lat, lon, 12)
		}
	}

	def onPostLoad() {
		//mapview.invalidate()
		onStopLoading()
		animateToCall()
	}

	override def reloadMap() {
		onStartLoading()
		onStopLoading()
	}

	override def onStartLoading() {
		//loading.setVisibility(View.VISIBLE)
	}

	override def onStopLoading() {
		//loading.setVisibility(View.GONE)
	}

	class MapItemGestureListener extends ItemizedLayer.OnItemGestureListener[Station] {
		override def onItemSingleTapUp(index: Int, item: Station): Boolean = false

		override def onItemLongPress(index: Int, item: Station): Boolean = false
	}
	class Station(override val call : String, override val origin : String, override val symbol : String,
									 override val lat : Double, override val lon : Double,
									 override val qrg : String, override val comment : String,
									 override val speed : Int, override val course : Int,
									 override val movelog : ArrayBuffer[Point])
		extends super.Station(call, origin, symbol, lat, lon, qrg, comment,
			speed, course, movelog) with MarkerInterface {

		lazy val drawSize = (getResources().getDisplayMetrics().density * 24).toInt

		override def getMarker: MarkerSymbol = {
      val ab = new AndroidBitmap(symbol2bitmap(symbol, drawSize))
			new MarkerSymbol(ab,  HotspotPlace.CENTER, false)
		}

		override def getPoint: GeoPoint = new GeoPoint(lat, lon)
	}

}
