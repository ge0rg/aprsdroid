package org.aprsdroid.app

import _root_.android.os.Bundle
import _root_.android.preference.PreferenceActivity

class PrefsAct extends PreferenceActivity {
	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		addPreferencesFromResource(R.xml.preferences)
	}
}
