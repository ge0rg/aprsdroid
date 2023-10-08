package org.aprsdroid.app

import _root_.android.Manifest
import _root_.android.os.Bundle
import _root_.android.content.{Context, Intent, SharedPreferences}
import _root_.android.content.SharedPreferences.OnSharedPreferenceChangeListener
import _root_.android.preference.{CheckBoxPreference, Preference, PreferenceActivity, PreferenceManager}
import android.location.LocationManager
import android.preference.Preference.OnPreferenceClickListener
import android.widget.Toast

class BackendPrefs extends PreferenceActivity
		with OnSharedPreferenceChangeListener
		with PermissionHelper {
	def loadXml() {
		val prefs = new PrefsWrapper(this)
		addPreferencesFromResource(R.xml.backend)
		addPreferencesFromResource(AprsBackend.prefxml_proto(prefs))
		val additional_xml = AprsBackend.prefxml_backend(prefs)
		if (additional_xml != 0) {
			addPreferencesFromResource(additional_xml)
			hookPasscode()
			hookGpsPermission()
		}
	}

	def hookPasscode(): Unit = {
		val p = findPreference("passcode")
		if (p != null) {
			p.setOnPreferenceClickListener(new OnPreferenceClickListener() {
				def onPreferenceClick(preference: Preference) = {
					new PasscodeDialog(BackendPrefs.this, false).show()
					true
				}
			});
		}
	}
	def hookGpsPermission(): Unit = {
		val p = findPreference("kenwood.gps")
		if (p != null) {
			p.setOnPreferenceClickListener(new OnPreferenceClickListener() {
				def onPreferenceClick(preference: Preference) = {
					if (preference.asInstanceOf[CheckBoxPreference].isChecked) {
						preference.asInstanceOf[CheckBoxPreference].setChecked(false)
						checkPermissions(Array(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_GPS)
					}
					true
				}
			});
		}
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

	val REQUEST_GPS = 1010

	override def getActionName(action: Int): Int = R.string.p_conn_kwd_gps

	override def onAllPermissionsGranted(action: Int): Unit = {
		findPreference("kenwood.gps").asInstanceOf[CheckBoxPreference].setChecked(true)
	}
	override def onPermissionsFailedCancel(action: Int): Unit = {
		// nop
	}
}
