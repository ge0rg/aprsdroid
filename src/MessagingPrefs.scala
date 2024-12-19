package org.aprsdroid.app

import _root_.android.os.Bundle
import _root_.android.preference.{PreferenceActivity, PreferenceManager}
import _root_.android.content.SharedPreferences
import _root_.android.content.SharedPreferences.OnSharedPreferenceChangeListener

class MessagingPrefs extends PreferenceActivity with OnSharedPreferenceChangeListener {

  lazy val prefs = new PrefsWrapper(this)

  // Load the preferences XML
  def loadXml(): Unit = {
    addPreferencesFromResource(R.xml.messaging) // Load the preferences from messaging.xml
  }

  // Called when the activity is created
  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    loadXml() // Load the XML file containing preferences
    getPreferenceScreen.getSharedPreferences.registerOnSharedPreferenceChangeListener(this) // Register listener for preference changes
  }

  // Called when the activity is destroyed
  override def onDestroy(): Unit = {
    super.onDestroy()
    getPreferenceScreen.getSharedPreferences.unregisterOnSharedPreferenceChangeListener(this) // Unregister listener
  }

  // Called when a shared preference is changed
  override def onSharedPreferenceChanged(sp: SharedPreferences, key: String): Unit = {
    key match {
      case "p.messaging" | "p.retry" | "p.ackdupetoggle" | "p.ackdupe" | "p.msgdupetoggle" | "p.msgdupetime" =>
        setPreferenceScreen(null) // Clear the current preference screen
        loadXml() // Reload the preferences to reflect any changes
      case _ => // Ignore other keys
    }
  }
}
