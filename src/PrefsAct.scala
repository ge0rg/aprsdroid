package org.aprsdroid.app

import _root_.android.content.Intent
import _root_.android.os.Bundle
import _root_.android.preference.PreferenceActivity
import _root_.android.preference.PreferenceManager

class PrefsAct extends PreferenceActivity {
	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		addPreferencesFromResource(R.xml.preferences)
	}

	override def startActivity(i : Intent) {
		if (Intent.ACTION_GET_CONTENT == i.getAction())
			startActivityForResult(Intent.createChooser(i, 
				getString(R.string.p_mapfile_choose)), 123456)
		else
			super.startActivity(i)
	}

	override def onActivityResult(reqCode : Int, resultCode : Int, data : Intent) {
		android.util.Log.d("PrefsAct", "onActResult: request=" + reqCode + " result=" + resultCode + " " + data)
		if (resultCode == android.app.Activity.RESULT_OK && reqCode == 123456) {
			val mapfile = data.getData().getScheme() match {
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
			if (mapfile != null) {
				PreferenceManager.getDefaultSharedPreferences(this)
					.edit().putString("mapfile", mapfile).commit()
				android.widget.Toast.makeText(this, mapfile,
					android.widget.Toast.LENGTH_SHORT).show()
			} else {
				android.widget.Toast.makeText(this, getString(R.string.mapfile_error, mapfile),
					android.widget.Toast.LENGTH_SHORT).show()
			}
		}
	}
}
