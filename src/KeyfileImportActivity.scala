package org.aprsdroid.app

import _root_.android.app.Activity
import _root_.android.app.AlertDialog
import _root_.android.content._
import _root_.android.os.Bundle
import _root_.android.preference.PreferenceManager
import _root_.android.text.InputType
import _root_.android.util.Log
import _root_.android.widget.{EditText, Toast}

import _root_.java.io.File
import _root_.java.io.FileOutputStream
import _root_.java.security.KeyStore
import _root_.java.security.cert.X509Certificate

import scala.collection.JavaConversions._ // for enumeration of keystore aliases

class KeyfileImportActivity extends Activity {
	val TAG = "APRSdroid.KeyImport"
	val KEYSTORE_PASS = "APRS".toCharArray()
	val KEYSTORE_DIR = "keystore"

	val CALL_RE = ".*CALLSIGN=([0-9A-Za-z]+).*".r

	lazy val db = StorageDatabase.open(this)

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		Log.d(TAG, "created: " + getIntent())
		query_for_password()
	}

	def query_for_password() {
		val pwd = new EditText(this)
		pwd.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
		val listener = new DialogInterface.OnClickListener() {
				override def onClick(d : DialogInterface, which : Int) {
					which match {
						case DialogInterface.BUTTON_POSITIVE =>
							import_key(pwd.getText().toString())
						case _ =>
							finish()
					}
				}}
		new AlertDialog.Builder(this).setTitle(R.string.ssl_import_activity)
			.setMessage(R.string.ssl_import_password)
			.setView(pwd)
			.setPositiveButton(android.R.string.ok, listener)
			.setNegativeButton(android.R.string.cancel, listener)
			.setOnCancelListener(new DialogInterface.OnCancelListener() {
				override def onCancel(d : DialogInterface) {
					finish()
				}})
			.create.show
	}

	def import_key(password : String) {
		try {
			val ks = KeyStore.getInstance("PKCS12")
			ks.load(getContentResolver().openInputStream(getIntent.getData()), password.toCharArray)
			var callsign : String = null
			for (alias <- ks.aliases()) {
				if (ks.isKeyEntry(alias)) {
					val c = ks.getCertificate(alias).asInstanceOf[X509Certificate]
					// work around missing X500Principal.getName(String, Map<String, String) on SDK<9:
					val dn = c.getSubjectX500Principal().toString()
						.replace("OID.1.3.6.1.4.1.12348.1.1=", "CALLSIGN=")
					Log.d(TAG, "Loaded key: " + dn)
					dn match {
					case CALL_RE(call) => callsign = call
					case _ =>
					}
				}
			}
			if (callsign != null) {
				val dir = getApplicationContext().getDir(KEYSTORE_DIR, Context.MODE_PRIVATE)
				val keyStoreFile = new File(dir + File.separator + callsign + ".p12")
				ks.store(new FileOutputStream(keyStoreFile), KEYSTORE_PASS)

				PreferenceManager.getDefaultSharedPreferences(this)
					.edit().putString("callsign", callsign).commit()

				val msg = getString(R.string.ssl_import_ok, callsign)
				Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
				db.addPost(System.currentTimeMillis(), StorageDatabase.Post.TYPE_INFO,
					getString(R.string.post_info), msg)
				startActivity(new Intent(this, classOf[LogActivity]))
			}
		} catch {
		case e : Exception =>
			val errmsg = getString(R.string.ssl_import_error, e.getMessage())
			Toast.makeText(this, errmsg, Toast.LENGTH_LONG).show()
			db.addPost(System.currentTimeMillis(), StorageDatabase.Post.TYPE_ERROR,
				getString(R.string.post_error), errmsg)
			e.printStackTrace()
		}
		finish()
	}
}
