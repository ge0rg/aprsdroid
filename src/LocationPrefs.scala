package org.aprsdroid.app

import _root_.android.content.Context
import _root_.android.location.{Location, LocationManager}
import _root_.android.os.Bundle
import _root_.android.preference.{PreferenceActivity, PreferenceManager}

class LocationPrefs extends PreferenceActivity {
	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		val prefs = new PrefsWrapper(this)
		if (checkGpsManualPosition(prefs)) {
			finish()
			return
		}
		addPreferencesFromResource(LocationSource.instanciatePrefsAct(prefs))
	}

	def checkGpsManualPosition(prefs : PrefsWrapper) = {
		val i = getIntent()
		if (i != null && i.getDataString() != null && i.getDataString().equals("gps2manual")) {
			val l = getSystemService(Context.LOCATION_SERVICE).asInstanceOf[LocationManager]
					.getLastKnownLocation(LocationManager.GPS_PROVIDER)
			if (l != null) {
				val pe = prefs.prefs.edit()
				pe.putString("manual_lat", l.getLatitude().toString())
				pe.putString("manual_lon", l.getLongitude().toString())
				pe.commit()
			}
			true
		}
		false
	}
}
