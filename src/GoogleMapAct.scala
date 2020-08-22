package org.aprsdroid.app

import java.util

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import com.google.android.gms.maps.GoogleMap.{OnCameraMoveListener, OnInfoWindowClickListener, OnMarkerClickListener}
import com.google.android.gms.maps.model.{BitmapDescriptor, BitmapDescriptorFactory, LatLng, Marker, MarkerOptions}
import com.google.android.gms.maps.{CameraUpdateFactory, GoogleMap, MapView, OnMapReadyCallback}
import com.google.maps.android.ui.IconGenerator

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

// to make scala-style iterating over arraylist possible
import scala.collection.JavaConversions._

class GoogleMapAct extends Activity with MapLoaderBase
                with OnMarkerClickListener
                with OnInfoWindowClickListener with OnCameraMoveListener {
        lazy val loading = findViewById(R.id.loading).asInstanceOf[View]
        lazy val mapview = findViewById(R.id.mapview).asInstanceOf[MapView]
        var map : GoogleMap = null
        lazy val icons = mutable.HashMap[String, BitmapDescriptor]()
        var visible_callsigns = true
        var first_load = true
        val CALLSIGN_ZOOM = 8

        override def onCreate(savedInstanceState: Bundle) {
                super.onCreate(savedInstanceState)
                setContentView(R.layout.googlemapview)

                mapview.onCreate(savedInstanceState)
                mapview.getMapAsync(new OnMapReadyCallback {
                        override def onMapReady(googleMap: GoogleMap): Unit = {
                                Log.d(TAG, "Got map!")
                                map = googleMap
                                setMapMode(MapModes.defaultMapMode(GoogleMapAct.this, prefs))
                                //not helpful, off at the top:
                                //map.setMyLocationEnabled(true)
                                map.setOnMarkerClickListener(GoogleMapAct.this)
                                map.setOnInfoWindowClickListener(GoogleMapAct.this)
                                map.setOnCameraMoveListener(GoogleMapAct.this)
                                map.getUiSettings().setCompassEnabled(true)
                                map.getUiSettings().setZoomControlsEnabled(true)
                                visible_callsigns = (map.getCameraPosition().zoom > CALLSIGN_ZOOM)
                                startLoading()
                        }
                })
                Log.d(TAG, "Creating bitmaps...")
                Log.d(TAG, "Done creating bitmaps...")
        }

        override def onLowMemory(): Unit = {
                super.onLowMemory()
                mapview.onLowMemory()
        }

        override def onStart(): Unit = {
                super.onStart()
                mapview.onStart()
        }

        override def onResume(): Unit = {
                super.onResume()
                setKeepScreenOn()
                setVolumeControls()
                mapview.onResume()
                if (targetcall != "")
                        startFollowStation(targetcall)
        }

        override def onSaveInstanceState(outState: Bundle): Unit = {
                super.onSaveInstanceState(outState)
                mapview.onSaveInstanceState(outState)
        }

        override def onPause(): Unit = {
                super.onPause()
                mapview.onPause()
        }

        override def onStop(): Unit = {
                super.onStop()
                mapview.onStop()
        }

        override def onDestroy(): Unit = {
                super.onDestroy()
                mapview.onDestroy()
        }

        override def setMapMode(mm : MapMode) = {
                mm match {
                case gmm : GoogleMapMode => 
                        map.setMapType(gmm.mapType)
                case _ =>
                        super.setMapMode(mm)
                }
        }

        override def onStartLoading() {
                loading.setVisibility(View.VISIBLE)
        }

        override def onStopLoading() {
                loading.setVisibility(View.GONE)
                if (targetcall != "") {
                        markers.get(targetcall) match {
                        case None =>
                        case Some(sta) => 
                                map.animateCamera(CameraUpdateFactory.newLatLngZoom(sta.icon.getPosition(), 14f))
                                if (first_load) {
                                        sta.icon.showInfoWindow()
                                        first_load = false
                                }
                        }
                }
        }

        class MarkerInfo(val icon : Marker, val label : Marker, var last_update : Int) {}

        val markers = new mutable.HashMap[String, MarkerInfo]()
        lazy val drawSize = (getResources().getDisplayMetrics().density * 24).toInt
        lazy val iconGenerator = initIconGenerator()

        def initIconGenerator(): IconGenerator = {
                val ig = new IconGenerator(this)
                ig.setBackground(null)
                ig.setTextAppearance(R.style.MapCallSign)
                ig.setContentPadding(0, 0, 0, 0)
                ig
        }

        def symbol2marker(symbol : String): BitmapDescriptor = {
                icons.get(symbol) match {
                case Some(desc) => desc
                case None =>
                        val desc = BitmapDescriptorFactory.fromBitmap(symbol2bitmap(symbol, drawSize))
                        icons(symbol) = desc
                        desc
                }
        }

        def onStationUpdate(stations : util.ArrayList[Station]): Unit = {
                for (sta <- stations) {
                        Log.d(TAG, "onStaUpdate: " + sta.call)
                        if (map == null)
                                return
                        val latlon = new LatLng(sta.lat, sta.lon)
                        markers.get(sta.call) match {
                        case Some(mi) =>
                                mi.icon.setPosition(latlon)
                                mi.label.setPosition(latlon)
                                mi.last_update = 1234
                        case None =>
                                val icon = map.addMarker(new MarkerOptions()
                                  .position(latlon)
                                  .anchor(0.5f, 0.5f) // center at the middle of the icon
                                  //.infoWindowAnchor(0.5f, 0.0f) // at the top of the icon
                                  .flat(true)
                                  .rotation(sta.course) // TODO fuckers! - the rotation will also influence the info window anchor
                                  .icon(symbol2marker(sta.symbol))
                                  .title(sta.callQrg())
                                  .snippet(sta.comment))
                                val label = map.addMarker(new MarkerOptions()
                                  .position(latlon)
                                  .icon(BitmapDescriptorFactory.fromBitmap(iconGenerator.makeIcon(sta.call)))
                                  .visible(visible_callsigns)
                                  .anchor(0.0f, -0.2f) // center text below the icon
                                )
                                icon.setTag(sta.call)
                                label.setTag(sta.call)
                                markers(sta.call) = new MarkerInfo(icon, label, 0)
                        }

                }
        }

        // OnMarkerClickListener
        override def onMarkerClick(marker : Marker): Boolean = {
                Log.d(TAG, "marker click: " + marker.getTitle() + " / " + marker.getTag())
                startFollowStation(marker.getTag().toString())
                false
        }

        // OnInfoWindowClickListener
        override def onInfoWindowClick(marker: Marker): Unit = {
                openDetails(marker.getTitle())
        }

        // OnCameraMoveListener
        override def onCameraMove(): Unit = {
                Log.d(TAG, "zoom level: " + map.getCameraPosition().zoom)
                val need_visible = (map.getCameraPosition().zoom > CALLSIGN_ZOOM)
                if (need_visible != visible_callsigns) {
                        visible_callsigns = need_visible
                        for ((call, marker) <- markers)
                                marker.label.setVisible(visible_callsigns)
                }

        }
}

