package org.aprsdroid.app

import _root_.android.Manifest
import _root_.android.content.{Context, Intent}
import _root_.android.location.{Location, LocationManager}
import _root_.android.os.Bundle
import _root_.android.content.SharedPreferences
import _root_.android.content.SharedPreferences.OnSharedPreferenceChangeListener
import _root_.android.preference.{PreferenceActivity, PreferenceManager}
import _root_.android.widget.Toast

class LocationPrefs extends PreferenceActivity with OnSharedPreferenceChangeListener with PermissionHelper {
	lazy val prefs = new PrefsWrapper(this)

	def loadXml() {
		addPreferencesFromResource(R.xml.location)
		addPreferencesFromResource(LocationSource.instanciatePrefsAct(prefs))
	}

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		loadXml()
		getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this)
	}
	override def onDestroy() {
		super.onDestroy()
		getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this)
	}

	override def onSharedPreferenceChanged(sp: SharedPreferences, key : String) {
		if (key == "loc_source" || key == "manual_lat" || key == "manual_lon") {
			setPreferenceScreen(null)
			loadXml()
		}
	}

	val REQUEST_GPS = 101
	val REQUEST_MAP = 102

	override def onNewIntent(i : Intent) {
		if (i != null && i.getDataString() != null) {
			i.getDataString() match {
			case "gps2manual" =>
				checkPermissions(Array(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_GPS)
			case "chooseOnMap" =>
				val mapmode = MapModes.defaultMapMode(this, prefs)
				startActivityForResult(new Intent(this, mapmode.viewClass).putExtra("info", R.string.p_source_from_map_save), REQUEST_MAP)
			case _ => // ignore
			}
		}
	}

	override def onActivityResult(reqCode : Int, resultCode : Int, data : Intent) {
		android.util.Log.d("LocationPrefs", "onActResult: request=" + reqCode + " result=" + resultCode + " " + data)
		if (resultCode == android.app.Activity.RESULT_OK && reqCode == REQUEST_MAP) {
			prefs.prefs.edit()
				.putString("manual_lat", data.getFloatExtra("lat", 0.0f).toString())
				.putString("manual_lon", data.getFloatExtra("lon", 0.0f).toString())
				.commit()
		} else
			super.onActivityResult(reqCode, resultCode, data)
	}
	override def getActionName(action: Int): Int = R.string.p_source_get_last

	override def onAllPermissionsGranted(action: Int): Unit = {
		val ls = getSystemService(Context.LOCATION_SERVICE).asInstanceOf[LocationManager]
		val l = ls.getLastKnownLocation(PeriodicGPS.bestProvider(ls))
		if (l != null) {
			val pe = prefs.prefs.edit()
			pe.putString("manual_lat", l.getLatitude().toString())
			pe.putString("manual_lon", l.getLongitude().toString())
			pe.commit()
		} else Toast.makeText(this, getString(R.string.map_track_unknown, prefs.getCallsign()), Toast.LENGTH_SHORT).show()
	}
	override def onPermissionsFailedCancel(action: Int): Unit = {
		// nop
	}
}
