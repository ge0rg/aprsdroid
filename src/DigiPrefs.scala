package org.aprsdroid.app

import _root_.android.content.SharedPreferences
import _root_.android.os.Bundle
import _root_.android.preference.{PreferenceActivity, PreferenceManager}

class DigiPrefs extends PreferenceActivity with SharedPreferences.OnSharedPreferenceChangeListener {

  lazy val prefs = new PrefsWrapper(this)

  def loadXml() {
    // Load digi.xml preferences
    addPreferencesFromResource(R.xml.digi)
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

  override def onSharedPreferenceChanged(sp: SharedPreferences, key: String) {
    // Handle preference changes for specific keys
    if (key == "p.digipeating" || key == "digipeater_path" || key == "p.dedupe" || key == "p.regenerate") {
      // Re-load preferences if critical preferences change
      setPreferenceScreen(null)
      loadXml()
    }
  }
}
