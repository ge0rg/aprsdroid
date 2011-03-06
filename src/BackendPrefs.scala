package org.aprsdroid.app

import _root_.android.os.Bundle
import _root_.android.preference.{PreferenceActivity, PreferenceManager}

class BackendPrefs extends PreferenceActivity {
	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		val prefs = new PrefsWrapper(this)
		addPreferencesFromResource(AprsIsUploader.instanciatePrefsAct(prefs))
	}
}
