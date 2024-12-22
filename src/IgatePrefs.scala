package org.aprsdroid.app

import _root_.android.content.SharedPreferences
import _root_.android.os.Bundle
import _root_.android.preference.{PreferenceActivity, CheckBoxPreference}
import android.util.Log

class IgatePrefs extends PreferenceActivity with SharedPreferences.OnSharedPreferenceChangeListener {

  lazy val prefs = new PrefsWrapper(this)

  def loadXml(): Unit = {
    // Load only the p.igating preference
    addPreferencesFromResource(R.xml.igate) // Ensure this XML only contains p.igating
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    loadXml()
    getPreferenceScreen().getSharedPreferences.registerOnSharedPreferenceChangeListener(this)

    // Update preferences state on activity creation
    updateCheckBoxState()
  }

  override def onDestroy(): Unit = {
    super.onDestroy()
    getPreferenceScreen().getSharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
  }

  override def onSharedPreferenceChanged(sp: SharedPreferences, key: String): Unit = {
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

	  // Check if the service is running using your logic
	  val isServiceRunning = prefs.getBoolean("service_running", false)

	  if (isServiceRunning) {
		// Disable the checkbox and update the summary
		igatingPref.setEnabled(false)
		igatingPref.setSummary("Setting disabled while the service is running.")
	  } else {
		// Enable the checkbox and restore the default summary
		igatingPref.setEnabled(true)
	  }
	}
}
