package org.aprsdroid.app

import _root_.android.content.SharedPreferences
import _root_.android.os.Bundle
import _root_.android.preference.{PreferenceActivity, CheckBoxPreference}

class IgatePrefs extends PreferenceActivity with SharedPreferences.OnSharedPreferenceChangeListener {

  lazy val prefs = new PrefsWrapper(this)

  def loadXml() {
    // Load only the p.igating preference
    addPreferencesFromResource(R.xml.igate)  // Ensure this XML only contains p.igating
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
      case "p.igating" =>
        // Handle changes to "p.igating" preference (if necessary)
        updateCheckBoxState()
      case _ => // No action for other preferences
    }
  }

  // This method will enable/disable the checkboxes based on their current state
  private def updateCheckBoxState(): Unit = {
    val igatingPref = findPreference("p.igating").asInstanceOf[CheckBoxPreference]
    
    // Add logic if needed to handle the "p.igating" preference state
    // For example, enabling or disabling other preferences based on this preference.
  }
}
