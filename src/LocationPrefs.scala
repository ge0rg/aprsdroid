package org.aprsdroid.app

import _root_.android.content.Context
import _root_.android.location.{Location, LocationManager}
import _root_.android.os.Bundle
import _root_.android.preference.{PreferenceActivity, PreferenceManager}

class LocationPrefs extends PreferenceActivity {

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		val prefs = new PrefsWrapper(this)
		addPreferencesFromResource(LocationSource.instanciatePrefsAct(prefs))
	}

	override def onNewIntent(i : android.content.Intent) {
		if (i != null && i.getDataString() != null && i.getDataString().equals("gps2manual")) {
			val l = getSystemService(Context.LOCATION_SERVICE).asInstanceOf[LocationManager]
					.getLastKnownLocation(LocationManager.GPS_PROVIDER)
			if (l != null) {
				val pe = new PrefsWrapper(this).prefs.edit()
				pe.putString("manual_lat", l.getLatitude().toString())
				pe.putString("manual_lon", l.getLongitude().toString())
				pe.commit()
			}
			finish()
			startActivity(i)
		}
	}
}
