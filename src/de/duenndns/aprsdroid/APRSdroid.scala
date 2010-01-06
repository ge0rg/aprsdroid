package de.duenndns.aprsdroid

import _root_.android.app.Activity
import _root_.android.content._
import _root_.android.location._
import _root_.android.os.Bundle
import _root_.android.preference.PreferenceManager
import _root_.android.util.Log
import _root_.android.view.View
import _root_.android.view.View.OnClickListener
import _root_.android.widget.Button
import _root_.android.widget.TextView
import _root_.android.widget.Toast

class APRSdroid extends Activity with LocationListener with OnClickListener {
	val TAG = "APRSdroid"

	lazy val prefs = PreferenceManager.getDefaultSharedPreferences(this)

	lazy val lat = findViewById(R.id.lat).asInstanceOf[TextView]
	lazy val lon = findViewById(R.id.lon).asInstanceOf[TextView]
	lazy val status = findViewById(R.id.status).asInstanceOf[TextView]

	lazy val singleBtn = findViewById(R.id.singlebtn).asInstanceOf[Button]
	lazy val startstopBtn = findViewById(R.id.startstopbtn).asInstanceOf[Button]
	lazy val prefsBtn = findViewById(R.id.preferencebtn).asInstanceOf[Button]

	lazy val locReceiver = new BroadcastReceiver() {
		override def onReceive(ctx : Context, i : Intent) {
			val l = i.getParcelableExtra(AprsService.LOCATION).asInstanceOf[Location]
			onLocationChanged(l)
			//status.setText(i.getParcelableExtra(AprsService.PACKET).asInstanceOf[String])
		}
	}

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.main)

		for (btn <- List(singleBtn, startstopBtn, prefsBtn)) {
			btn.setOnClickListener(this);
		}

		registerReceiver(locReceiver, new IntentFilter(AprsService.UPDATE))
	}

	override def onResume() {
		super.onResume()
		for (p <- List("callsign", "passcode", "host")) {
			if (!prefs.contains(p)) {
				startActivity(new Intent(this, classOf[PrefsAct]));
				Toast.makeText(this, R.string.firstrun, Toast.LENGTH_SHORT).show()
				return
			}
		}
		setupButtons(AprsService.running)
	}

	override def onDestroy() {
		super.onDestroy()
		unregisterReceiver(locReceiver)
	}

	override def onLocationChanged(location : Location) {
		Log.d(TAG, "onLocationChanged: " + location)
		lat.setText("lat: " + location.getLatitude)
		lon.setText("lon: " + location.getLongitude)
	}
	override def onProviderDisabled(provider : String) {
		Log.d(TAG, "onProviderDisabled: " + provider)
		status.setText(provider + " disabled")
	}
	override def onProviderEnabled(provider : String) {
		Log.d(TAG, "onProviderEnabled: " + provider)
		status.setText(provider + " enabled")
	}
	override def onStatusChanged(provider : String, st: Int, extras : Bundle) {
		Log.d(TAG, "onStatusChanged: " + provider)
		status.setText("status: " + provider + "/" + st);
	}

	def serviceIntent(action : String) : Intent = {
		new Intent(action, null, this, classOf[AprsService])
	}

	def setupButtons(running : Boolean) {
		singleBtn.setEnabled(!running)
		if (running) {
			startstopBtn.setText(R.string.stoplog)
		} else {
			startstopBtn.setText(R.string.startlog)
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
		case R.id.preferencebtn =>
			startActivity(new Intent(this, classOf[PrefsAct]));
		case _ =>
			status.setText(view.asInstanceOf[Button].getText)
		}
	}

}

