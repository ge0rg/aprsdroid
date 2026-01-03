package org.aprsdroid.app

import _root_.android.content.SharedPreferences
import _root_.android.os.Bundle
import _root_.android.preference.{PreferenceActivity, PreferenceManager, CheckBoxPreference, ListPreference}
import _root_.android.util.Log

class CompressedPrefs extends PreferenceActivity with SharedPreferences.OnSharedPreferenceChangeListener {

  lazy val prefs = new PrefsWrapper(this)

  def loadXml() {
    // Load compressed.xml preferences
    addPreferencesFromResource(R.xml.compressed)
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
    // Handle preference changes for specific keys
    key match {
      case "compressed_location" | "compressed_mice" =>
        // Update checkbox states when either preference changes
        updateCheckBoxState()
      case "p__location_mice_status" =>
        // Handle changes for the p__location_mice_status preference (if needed)
        updateStatus()
      case _ => // No action for other preferences
    }
  }

	// This method will enable/disable the checkboxes based on their current state
	private def updateCheckBoxState(): Unit = {
	  val compressedLocationPref = findPreference("compressed_location").asInstanceOf[CheckBoxPreference]
	  val compressedMicePref = findPreference("compressed_mice").asInstanceOf[CheckBoxPreference]
	  val locationMiceStatusPref = findPreference("p__location_mice_status").asInstanceOf[ListPreference]

	  // If "compressed_location" is checked, disable "p__location_mice_status"
	  if (compressedLocationPref.isChecked) {
		locationMiceStatusPref.setEnabled(false)
		compressedMicePref.setEnabled(false)  // Also disable "compressed_mice" when "compressed_location" is checked
	  } else {
		locationMiceStatusPref.setEnabled(true)  // Enable "p__location_mice_status" when "compressed_location" is not checked
		compressedMicePref.setEnabled(true)  // Re-enable "compressed_mice" if "compressed_location" is unchecked
	  }

	  // If "compressed_mice" is checked, disable "compressed_location"
	  if (compressedMicePref.isChecked) {
		compressedLocationPref.setEnabled(false)
	  } else {
		compressedLocationPref.setEnabled(true)
	  }
	}


  // Method to handle updates related to p__location_mice_status
  private def updateStatus(): Unit = {
    val statusPref = findPreference("p__location_mice_status").asInstanceOf[ListPreference]
    val statusValue = statusPref.getValue

    // Here, you can handle actions based on the selected status.
    // For example, logging the selected status:
    Log.d("CompressedPrefs", s"Selected Location Mice Status: $statusValue")
  }
}
