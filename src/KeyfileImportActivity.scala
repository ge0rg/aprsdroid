package org.aprsdroid.app

import _root_.android.app.Activity
import _root_.android.content.Context
import _root_.android.os.Bundle
import _root_.android.preference.PreferenceManager
import _root_.android.util.Log
import _root_.android.widget.Toast

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

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		Log.d(TAG, "created: " + getIntent())
		try {
			val ks = KeyStore.getInstance("PKCS12")
			ks.load(getContentResolver().openInputStream(getIntent.getData()), KEYSTORE_PASS)
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

				Toast.makeText(this, getString(R.string.ssl_import_ok, callsign), Toast.LENGTH_SHORT).show()
			}
		} catch {
		case e : Exception =>
			Toast.makeText(this, getString(R.string.ssl_import_error, e.getMessage()), Toast.LENGTH_SHORT).show()
			e.printStackTrace()
		}
		finish()
	}
}
