package de.duenndns.aprsdroid

import _root_.android.os.Bundle
import _root_.android.preference.{PreferenceActivity, PreferenceManager}

class BackendPrefs extends PreferenceActivity {
	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		val prefs = PreferenceManager.getDefaultSharedPreferences(this)
		addPreferencesFromResource(AprsIsUploader.instanciatePrefsAct(prefs))
	}
}
