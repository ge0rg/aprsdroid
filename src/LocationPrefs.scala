package org.aprsdroid.app

import _root_.android.Manifest
import _root_.android.content.Context
import _root_.android.location.{Location, LocationManager}
import _root_.android.os.Bundle
import _root_.android.content.SharedPreferences
import _root_.android.content.SharedPreferences.OnSharedPreferenceChangeListener
import _root_.android.preference.{PreferenceActivity, PreferenceManager}
import _root_.android.widget.Toast

class LocationPrefs extends PreferenceActivity with OnSharedPreferenceChangeListener with PermissionHelper {
	def loadXml() {
		val prefs = new PrefsWrapper(this)
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

	override def onNewIntent(i : android.content.Intent) {
		if (i != null && i.getDataString() != null && i.getDataString().equals("gps2manual")) {
			checkPermissions(Array(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_GPS)
		}
	}

	val REQUEST_GPS = 1010

	override def getActionName(action: Int): Int = R.string.p_source_get_last

	override def onAllPermissionsGranted(action: Int): Unit = {
		val l = getSystemService(Context.LOCATION_SERVICE).asInstanceOf[LocationManager]
			.getLastKnownLocation(LocationManager.GPS_PROVIDER)
		val prefs = new PrefsWrapper(this)
		if (l != null) {
			val pe = prefs.prefs.edit()
			pe.putString("manual_lat", l.getLatitude().toString())
			pe.putString("manual_lon", l.getLongitude().toString())
			pe.commit()
		} else Toast.makeText(this, getString(R.string.map_track_unknown, prefs.getCallsign()), Toast.LENGTH_SHORT).show()
	}
}
