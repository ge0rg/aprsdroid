package org.aprsdroid.app

import _root_.android.content.Intent
import _root_.android.net.Uri
import _root_.android.os.{Build, Bundle, Environment}
import _root_.android.preference.Preference
import _root_.android.preference.Preference.OnPreferenceClickListener
import _root_.android.preference.PreferenceActivity
import _root_.android.preference.PreferenceManager
import _root_.android.view.{Menu, MenuItem}
import _root_.android.widget.Toast
import java.text.SimpleDateFormat
import java.io.{File, PrintWriter}
import java.util.Date

import org.json.JSONObject

class PrefsAct extends PreferenceActivity {
	lazy val db = StorageDatabase.open(this)
	lazy val prefs = new PrefsWrapper(this)

	def exportPrefs() {
		val filename = "profile-%s.aprs".format(new SimpleDateFormat("yyyyMMdd-HHmm").format(new Date()))
		val directory = UIHelper.getExportDirectory(this)
		val file = new File(directory, filename)
		try {
			directory.mkdirs()
			val prefs = PreferenceManager.getDefaultSharedPreferences(this)
			val allPrefs = prefs.getAll
			allPrefs.remove("map_zoom")
			val json = new JSONObject(allPrefs)
			val fo = new PrintWriter(file)
			fo.println(json.toString(2))
			fo.close()

			UIHelper.shareFile(this, file, filename)
		} catch {
			case e : Exception => Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show()
		}
	}

	def fileChooserPreference(pref_name : String, reqCode : Int, titleId : Int) {
		findPreference(pref_name).setOnPreferenceClickListener(new OnPreferenceClickListener() {
			def onPreferenceClick(preference : Preference) = {
				val get_file = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
				startActivityForResult(Intent.createChooser(get_file,
					getString(titleId)), reqCode)
				true
			}
		});
	}
	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		addPreferencesFromResource(R.xml.preferences)
		//fileChooserPreference("mapfile", 123456, R.string.p_mapfile_choose)
		//fileChooserPreference("themefile", 123457, R.string.p_themefile_choose)
	}
	override def onResume() {
		super.onResume()
		findPreference("p_connsetup").setSummary(prefs.getBackendName())
		findPreference("p_location").setSummary(prefs.getLocationSourceName())
		findPreference("p_symbol").setSummary(getString(R.string.p_symbol_summary) + ": " + prefs.getString("symbol", "/$"))
	}

	def resolveContentUri(uri : Uri) = {
		val Array(storage, path) = uri.getPath().replace("/document/", "").split(":", 2)
		android.util.Log.d("PrefsAct", "resolveContentUri s=" + storage + " p=" + path)
		if (storage == "primary")
			Environment.getExternalStorageDirectory() + "/" + path
		else
			"/storage/" + storage + "/" + path
	}

	def parseFilePickerResult(data : Intent, pref_name : String, error_id : Int) {
		val file = data.getData().getScheme() match {
		case "file" =>
			data.getData().getPath()
		case "content" =>
			// fix up Uri for KitKat+; http://stackoverflow.com/a/20559175/539443
			// http://stackoverflow.com/a/27271131/539443
			if ("com.android.externalstorage.documents".equals(data.getData().getAuthority())) {
				resolveContentUri(data.getData())
			} else {
				val fixup_uri = Uri.parse(data.getDataString().replace(
					"content://com.android.providers.downloads.documents/document",
					"content://downloads/public_downloads"))
				val cursor = getContentResolver().query(fixup_uri, null, null, null, null)
				cursor.moveToFirst()
				val idx = cursor.getColumnIndex("_data")
				val result = if (idx != -1) cursor.getString(idx) else null
				cursor.close()
				result
			}
		case _ =>
			null
		}
		if (file != null) {
			PreferenceManager.getDefaultSharedPreferences(this)
				.edit().putString(pref_name, file).commit()
			Toast.makeText(this, file, Toast.LENGTH_SHORT).show()
			// reload prefs
			finish()
			startActivity(getIntent())
		} else {
			val errmsg = getString(error_id, data.getDataString())
			Toast.makeText(this, errmsg, Toast.LENGTH_SHORT).show()
			db.addPost(System.currentTimeMillis(), StorageDatabase.Post.TYPE_ERROR,
				getString(R.string.post_error), errmsg)
		}
	}

	override def onActivityResult(reqCode : Int, resultCode : Int, data : Intent) {
		android.util.Log.d("PrefsAct", "onActResult: request=" + reqCode + " result=" + resultCode + " " + data)
		if (resultCode == android.app.Activity.RESULT_OK && reqCode == 123456) {
			//parseFilePickerResult(data, "mapfile", R.string.mapfile_error)
			val takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
			getContentResolver.takePersistableUriPermission(data.getData(), takeFlags)
			PreferenceManager.getDefaultSharedPreferences(this)
				//.edit().putString("mapfile", data.getDataString()).commit()
			finish()
			startActivity(getIntent())
		} else
		if (resultCode == android.app.Activity.RESULT_OK && reqCode == 123457) {
			parseFilePickerResult(data, "themefile", R.string.themefile_error)
		} else
		if (resultCode == android.app.Activity.RESULT_OK && reqCode == 123458) {
			data.setClass(this, classOf[ProfileImportActivity])
			startActivity(data)
		} else
			super.onActivityResult(reqCode, resultCode, data)
	}

	override def onCreateOptionsMenu(menu : Menu) : Boolean = {
		getMenuInflater().inflate(R.menu.options_prefs, menu)
		true
	}
	override def onOptionsItemSelected(mi : MenuItem) : Boolean = {
		mi.getItemId match {
		case R.id.profile_load =>
			val get_file = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
			// TODO: use MaterialFilePicker().withFilter() for *.aprs
			startActivityForResult(Intent.createChooser(get_file,
				getString(R.string.profile_import_activity)), 123458)
			true
		case R.id.profile_export =>
			exportPrefs()
			true
		case _ => super.onOptionsItemSelected(mi)
		}
	}
}
