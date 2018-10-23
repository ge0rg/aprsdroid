package org.aprsdroid.app

import android.app.Activity
import android.content._
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.InputType
import android.util.Log
import android.widget.{EditText, Toast}

import java.io.File
import java.util.Scanner

import org.json._

import scala.collection.JavaConversions._ // for enumeration of config items

class ProfileImportActivity extends Activity {
	val TAG = "APRSdroid.ProfileImport"

	lazy val db = StorageDatabase.open(this)

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		Log.d(TAG, "created: " + getIntent())
		import_config()
	}

	def import_config() {
		try {
			// parse stream into string, http://stackoverflow.com/a/5445161/539443
			val scanner = new Scanner(getContentResolver().openInputStream(getIntent.getData())).useDelimiter("\\A")
			val config_string = scanner.next()
			val config = new JSONObject(config_string)
			val prefsedit = PreferenceManager.getDefaultSharedPreferences(this).edit()

                        val keys = config.keys()
			while (keys.hasNext()) {
                                val item = keys.next().asInstanceOf[String]
				val value = config.get(item)
				Log.d(TAG, "reading: " + item + " = " + value + "/" + value.getClass())

                                // Hack: too complicated to figure out scala match rules for native types
				value.getClass().getSimpleName() match {
				case "String" => prefsedit.putString(item, config.getString(item))
				case "Boolean" => prefsedit.putBoolean(item, config.getBoolean(item))
				case "Int" => prefsedit.putInt(item, config.getInt(item))
				}
			}
			prefsedit.commit()
			val msg = getString(R.string.profile_import_done, getIntent.getData().getPath())
			Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
			db.addPost(System.currentTimeMillis(), StorageDatabase.Post.TYPE_INFO,
				getString(R.string.profile_import_activity), msg)
			startActivity(new Intent(this, classOf[LogActivity]))
		} catch {
			case e : Exception =>
				val errmsg = getString(R.string.profile_import_error, e.getMessage())
				Toast.makeText(this, errmsg, Toast.LENGTH_LONG).show()
				db.addPost(System.currentTimeMillis(), StorageDatabase.Post.TYPE_ERROR,
					getString(R.string.profile_import_activity), errmsg)
				e.printStackTrace()
		}
		finish()
	}
}
