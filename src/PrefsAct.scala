package org.aprsdroid.app

import _root_.android.content.Intent
import _root_.android.os.Bundle
import _root_.android.preference.Preference
import _root_.android.preference.Preference.OnPreferenceClickListener
import _root_.android.preference.PreferenceActivity
import _root_.android.preference.PreferenceManager

class PrefsAct extends PreferenceActivity {
	def fileChooserPreference(pref_name : String, reqCode : Int) {
		findPreference(pref_name).setOnPreferenceClickListener(new OnPreferenceClickListener() {
			def onPreferenceClick(preference : Preference) = {
				val get_file = new Intent(Intent.ACTION_GET_CONTENT).setType("*/*")
				startActivityForResult(Intent.createChooser(get_file,
					getString(R.string.p_mapfile_choose)), reqCode)
				true
			}
		});
	}
	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		addPreferencesFromResource(R.xml.preferences)
		fileChooserPreference("mapfile", 123456)
	}

	def parseFilePickerResult(data : Intent, pref_name : String, error_id : Int) {
		val file = data.getData().getScheme() match {
		case "file" =>
			data.getData().getPath()
		case "content" =>
			val cursor = getContentResolver().query(data.getData(), null, null, null, null)
			cursor.moveToFirst()
			val idx = cursor.getColumnIndex("_data")
			val result = if (idx != -1) cursor.getString(idx) else null
			cursor.close()
			result
		case _ =>
			null
		}
		if (file != null) {
			PreferenceManager.getDefaultSharedPreferences(this)
				.edit().putString(pref_name, file).commit()
			android.widget.Toast.makeText(this, file,
				android.widget.Toast.LENGTH_SHORT).show()
		} else {
			android.widget.Toast.makeText(this, getString(error_id, file),
				android.widget.Toast.LENGTH_SHORT).show()
		}
	}

	override def onActivityResult(reqCode : Int, resultCode : Int, data : Intent) {
		android.util.Log.d("PrefsAct", "onActResult: request=" + reqCode + " result=" + resultCode + " " + data)
		if (resultCode == android.app.Activity.RESULT_OK && reqCode == 123456) {
			parseFilePickerResult(data, "mapfile", R.string.mapfile_error)
		}
	}
}
