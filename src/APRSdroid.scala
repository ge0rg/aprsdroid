package de.duenndns.aprsdroid

import _root_.android.app.Activity
import _root_.android.content._
import _root_.android.location._
import _root_.android.os.Bundle
import _root_.android.preference.PreferenceManager
import _root_.java.text.SimpleDateFormat
import _root_.android.util.Log
import _root_.android.view.{Menu, MenuItem, View}
import _root_.android.view.View.OnClickListener
import _root_.android.widget.Button
import _root_.android.widget.TextView
import _root_.android.widget.Toast
import _root_.java.util.Date

class APRSdroid extends Activity with OnClickListener {
	val TAG = "APRSdroid"

	lazy val prefs = PreferenceManager.getDefaultSharedPreferences(this)

	lazy val title = findViewById(R.id.title).asInstanceOf[TextView]
	lazy val latlon = findViewById(R.id.latlon).asInstanceOf[TextView]
	lazy val packet = findViewById(R.id.packet).asInstanceOf[TextView]
	lazy val status = findViewById(R.id.status).asInstanceOf[TextView]

	lazy val singleBtn = findViewById(R.id.singlebtn).asInstanceOf[Button]
	lazy val startstopBtn = findViewById(R.id.startstopbtn).asInstanceOf[Button]

	lazy val locReceiver = new BroadcastReceiver() {
		override def onReceive(ctx : Context, i : Intent) {
			val l = i.getParcelableExtra(AprsService.LOCATION).asInstanceOf[Location]
			if (l != null)
				onLocationChanged(l)
			val s = i.getStringExtra(AprsService.STATUS)
			if (s != null) {
				val timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date())
				lastPost.status = timestamp + " " + s
				status.setText(lastPost.status)
			}
			val p = i.getStringExtra(AprsService.PACKET)
			if (p != null) {
				Log.d(TAG, "received " + p)
				lastPost.packet = p
				packet.setText(p)
			}
			setupButtons(AprsService.running)
		}
	}

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.main)

		singleBtn.setOnClickListener(this);
		startstopBtn.setOnClickListener(this);

		registerReceiver(locReceiver, new IntentFilter(AprsService.UPDATE))
	}

	override def onResume() {
		super.onResume()
		val callsign = prefs.getString("callsign", "")
		val passcode = prefs.getString("passcode", "")
		if (callsign == "" || passcode == "") {
			startActivity(new Intent(this, classOf[PrefsAct]));
			Toast.makeText(this, R.string.firstrun, Toast.LENGTH_SHORT).show()
			return
		}
		val genpasscode = AprsPacket.passcode(callsign)
		if (passcode != genpasscode.toString()) {
			startActivity(new Intent(this, classOf[PrefsAct]));
			Toast.makeText(this, R.string.wrongpasscode, Toast.LENGTH_SHORT).show()
		}
		val callssid = AprsPacket.formatCallSsid(callsign, prefs.getString("ssid", ""))
		title.setText(getString(R.string.app_name) + ": " + callssid)
		latlon.setText(lastPost.latlon)
		status.setText(lastPost.status)
		packet.setText(lastPost.packet)
		setupButtons(AprsService.running)
	}

	override def onDestroy() {
		super.onDestroy()
		unregisterReceiver(locReceiver)
	}

	def onLocationChanged(location : Location) {
		lastPost.latlon = "lat: %1.4f  lon: %1.4f".format(location.getLatitude, location.getLongitude)
		latlon.setText(lastPost.latlon)
	}
	def serviceIntent(action : String) : Intent = {
		new Intent(action, null, this, classOf[AprsService])
	}

	override def onCreateOptionsMenu(menu : Menu) : Boolean = {
		getMenuInflater().inflate(R.menu.options, menu);
		true
	}

	def setupButtons(running : Boolean) {
		singleBtn.setEnabled(!running)
		if (running) {
			startstopBtn.setText(R.string.stoplog)
		} else {
			startstopBtn.setText(R.string.startlog)
		}
	}

	override def onOptionsItemSelected(mi : MenuItem) : Boolean = {
		mi.getItemId match {
		case R.id.preferences =>
			startActivity(new Intent(this, classOf[PrefsAct]));
			true
		case R.id.quit =>
			stopService(serviceIntent(AprsService.SERVICE))
			finish();
			true
		case _ => false
		}
	}

	override def onClick(view : View) {
		Log.d(TAG, "onClick: " + view + "/" + view.getId)

		view.getId match {
		case R.id.singlebtn =>
			startService(serviceIntent(AprsService.SERVICE_ONCE))
		case R.id.startstopbtn =>
			val is_running = AprsService.running
			if (!is_running) {
				startService(serviceIntent(AprsService.SERVICE))
			} else {
				stopService(serviceIntent(AprsService.SERVICE))
			}
			setupButtons(!is_running)
		case _ =>
			status.setText(view.asInstanceOf[Button].getText)
		}
	}

}

object lastPost {
	var latlon = "<Coordinates>"
	var status = "No events in the log"
	var packet = ""
}

