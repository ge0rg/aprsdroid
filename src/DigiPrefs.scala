package org.aprsdroid.app

import _root_.android.content.SharedPreferences
import _root_.android.os.Bundle
import _root_.android.preference.{PreferenceActivity, CheckBoxPreference}

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

    // Update preferences state on activity creation
    updateCheckBoxState()
  }

  override def onDestroy() {
    super.onDestroy()
    getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this)
  }

  override def onSharedPreferenceChanged(sp: SharedPreferences, key: String) {
    key match {
      case "p.digipeating" | "p.regenerate" =>
        // Update checkbox states when either preference changes
        updateCheckBoxState()
      case _ => // No action for other preferences
    }
  }

  // This method will enable/disable the checkboxes based on their current state
  private def updateCheckBoxState(): Unit = {
    val digipeatingPref = findPreference("p.digipeating").asInstanceOf[CheckBoxPreference]
    val regeneratePref = findPreference("p.regenerate").asInstanceOf[CheckBoxPreference]

    // If "p.digipeating" is checked, disable "p.regenerate"
    if (digipeatingPref.isChecked) {
      regeneratePref.setEnabled(false)
    } else {
      regeneratePref.setEnabled(true)
    }

    // If "p.regenerate" is checked, disable "p.digipeating"
    if (regeneratePref.isChecked) {
      digipeatingPref.setEnabled(false)
    } else {
      digipeatingPref.setEnabled(true)
    }
  }
}
