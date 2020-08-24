package org.aprsdroid.app

import android.os.Bundle
import android.util.Log
import android.view.{Menu, MenuItem}

trait MapMenuHelper extends UIHelper {
        val TAG = "APRSdroid.MapMenu"

        var targetcall = ""
        var showObjects = false

        override def onCreate(savedInstanceState: Bundle) {
                super.onCreate(savedInstanceState)
                targetcall = getTargetCall()
                showObjects = prefs.getShowObjects()
        }


        abstract override def onCreateOptionsMenu(menu : Menu) : Boolean = {
                getMenuInflater().inflate(R.menu.options_map, menu);
                getMenuInflater().inflate(R.menu.context_call, menu);
                getMenuInflater().inflate(R.menu.options_activities, menu);
                getMenuInflater().inflate(R.menu.options, menu);
                menu.findItem(R.id.map).setVisible(false)
                true
        }
        abstract override def onPrepareOptionsMenu(menu : Menu) : Boolean = {
                super.onPrepareOptionsMenu(menu)
                val tracking = (targetcall != "")
                        Log.d(TAG, "preparing menu for " + targetcall)
                menu.findItem(R.id.objects).setChecked(prefs.getShowObjects())
                menu.setGroupVisible(R.id.menu_context_call, tracking)
                menu.setGroupVisible(R.id.menu_options_activities, !tracking)
                menu.setGroupVisible(R.id.menu_options, !tracking)
                val modesmenu = menu.findItem(R.id.overlays).getSubMenu()
                        val defaultMapMode = MapModes.defaultMapMode(this, prefs)
                        Log.d(TAG, "default " + defaultMapMode.tag)
                for (mode <- MapModes.all_mapmodes) {
                        val item_found = modesmenu.findItem(mode.menu_id) 
                                val item = if (item_found != null) item_found else 
                        modesmenu.add(R.id.mapmodes, mode.menu_id, 0, mode.title)
                        Log.d(TAG, "menu item " + item.getTitle + " for " + mode.tag)
                        item.setCheckable(true)
                        if (mode == defaultMapMode)
                                item.setChecked(true)
                        item.setEnabled(mode.isAvailable(this))
                }
                true
        }

        def getTargetCall() : String = {
                val i = getIntent()
                        if (i != null && i.getDataString() != null) {
                        i.getDataString()
                } else ""
        }

        def startFollowStation(call : String) = {
                targetcall = call
                setLongTitle(R.string.app_map, targetcall)
                invalidateOptionsMenu()
        }

        def stopFollowStation() = {
                targetcall = ""
                setLongTitle(R.string.app_map, null)
                invalidateOptionsMenu()
        }

        def switchMapActivity(cls : Class[_]) = {
                MapModes.startMap(this, prefs, targetcall)
                finish()
        }

        def setMapMode(mm : MapMode) = {
                switchMapActivity(mm.viewClass)
        }

        def onMapModeItem(mi : MenuItem, mm : MapMode): Boolean = {
                MapModes.setDefault(prefs, mm.tag)
                setMapMode(mm)
                mi.setChecked(true)
                true
        }

        abstract override def onOptionsItemSelected(mi : MenuItem) : Boolean = {
                val mapmode = MapModes.fromMenuItem(mi)
                if (mapmode != null)
                        return onMapModeItem(mi, mapmode)
                mi.getItemId match {
                        //case R.id.normal | R.id.hybrid | R.id.satellite | R.id.mapsforge =>
                        //    onMapModeItem(mi)
                        case R.id.objects =>
                        val newState = prefs.toggleBoolean("show_objects", true)
                                mi.setChecked(newState)
                        showObjects = newState
                        reloadMap()
                        true
                        case _ =>
                        if (targetcall != "" && callsignAction(mi.getItemId, targetcall))
                                true
                        else
                                super.onOptionsItemSelected(mi)
                }
        }

        def reloadMap()

	def saveMapViewPosition(lat : Float, lon : Float, zoom : Float) {
		val edit = prefs.prefs.edit()
		edit.putFloat("map_lat", lat)
		edit.putFloat("map_lon", lon)
		edit.putFloat("map_zoom", zoom)
		edit.commit()
	}
	def loadMapViewPosition(lat : Float, lon : Float, zoom : Float)
		def loadMapViewPosition() {
		val lat = prefs.prefs.getFloat("map_lat", 52.5075f)
		val lon = prefs.prefs.getFloat("map_lon", 13.39027f)
		val zoom = prefs.prefs.getFloat("map_zoom", 12.0f)
		loadMapViewPosition(lat, lon, zoom)
	}

}
