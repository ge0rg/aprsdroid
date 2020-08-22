package org.aprsdroid.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.MenuItem

import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.GoogleMap

object MapModes {
	val all_mapmodes = new scala.collection.mutable.ArrayBuffer[MapMode]()

	def initialize(ctx : Context) {
		if (all_mapmodes.size > 0)
			return
		all_mapmodes += new GoogleMapMode("google", R.id.normal, null, GoogleMap.MAP_TYPE_NORMAL)
		all_mapmodes += new GoogleMapMode("satellite", R.id.satellite, null, GoogleMap.MAP_TYPE_HYBRID)
		all_mapmodes += new MapsforgeOnlineMode("osm", R.id.mapsforge, null, "TODO")
	}

	def reloadOfflineMaps(ctx : Context) {
	}

	def defaultMapMode(ctx : Context, prefs : PrefsWrapper): MapMode = {
		val tag = prefs.getString("mapmode", "google")
		android.util.Log.d("MapModes", "tag is " + tag )
		var default : MapMode = null
		for (mode <- all_mapmodes) {
			android.util.Log.d("MapModes", "mode " + mode.tag + " isA=" + mode.isAvailable(ctx))
			if (default == null && mode.isAvailable(ctx))
				default = mode
			if (mode.tag == tag && mode.isAvailable(ctx)) {
				android.util.Log.d("MapModes", "mode " + mode.tag + " is tagged")
				return mode
			}
		}
		android.util.Log.d("MapModes", "mode " + default.tag + " is default")
		return default
	}

	def startMap(ctx : Context, prefs : PrefsWrapper, targetcall : String) {
		val mm = defaultMapMode(ctx, prefs)
                val intent = new Intent(ctx, mm.viewClass)
                if (targetcall != "")
                        intent.setData(Uri.parse(targetcall))
                else
                        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                ctx.startActivity(intent)
	}

	def setDefault(prefs : PrefsWrapper, tag : String) {
		prefs.set("mapmode", tag)
	}

	def fromMenuItem(mi : MenuItem) : MapMode = {
		for (mode <- all_mapmodes) {
			if (mode.menu_id == mi.getItemId())
				return mode
		}
	        return null
	}


}

class MapMode(val tag : String, val menu_id : Int, val title : String, val viewClass : Class[_]) {
	def isAvailable(ctx : Context) = true
}

class GoogleMapMode(tag : String, menu_id : Int, title : String, val mapType : Int)
		extends MapMode(tag, menu_id, title, classOf[GoogleMapAct]) {
	override def isAvailable(ctx : Context) = {
		try {
			ctx.getPackageManager().getPackageInfo(GoogleApiAvailability.GOOGLE_PLAY_SERVICES_PACKAGE, 0)
			true
		} catch {
			case e : PackageManager.NameNotFoundException => false
		}
	}
}

class MapsforgeOnlineMode(tag : String, menu_id : Int, title : String, val foo : String)
		extends MapMode(tag, menu_id, title, classOf[MapAct]) {
}

class MapsforgeFileMode(tag : String, menu_id : Int, title : String, val file : String)
		extends MapMode(tag, menu_id, title, classOf[MapAct]) {
}

