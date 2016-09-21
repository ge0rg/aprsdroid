package org.aprsdroid.app

import _root_.android.os.Bundle
import _root_.android.content.SharedPreferences
import _root_.android.content.SharedPreferences.OnSharedPreferenceChangeListener
import _root_.android.preference.{PreferenceActivity, PreferenceManager}

class BackendPrefs extends PreferenceActivity with OnSharedPreferenceChangeListener {
	def loadXml() {
		val prefs = new PrefsWrapper(this)
		addPreferencesFromResource(R.xml.backend)
		addPreferencesFromResource(AprsBackend.prefxml_proto(prefs))
		val additional_xml = AprsBackend.prefxml_backend(prefs)
		if (additional_xml != 0)
			addPreferencesFromResource(additional_xml)
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
		if (key == "proto" || key == "link" || key == "aprsis") {
			setPreferenceScreen(null)
			loadXml()
		}
	}
}
