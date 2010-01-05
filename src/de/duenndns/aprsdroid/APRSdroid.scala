package de.duenndns.aprsdroid

import _root_.android.app.Activity
import _root_.android.content._
import _root_.android.location._
import _root_.android.os.Bundle
import _root_.android.util.Log
import _root_.android.view.View
import _root_.android.view.View.OnClickListener
import _root_.android.widget.Button
import _root_.android.widget.TextView

class APRSdroid extends Activity with LocationListener with OnClickListener {
	val TAG = "APRSdroid"

	lazy val lat = findViewById(R.id.lat).asInstanceOf[TextView]
	lazy val lon = findViewById(R.id.lon).asInstanceOf[TextView]
	lazy val status = findViewById(R.id.status).asInstanceOf[TextView]

	lazy val singleBtn = findViewById(R.id.singlebtn).asInstanceOf[Button]
	lazy val startstopBtn = findViewById(R.id.startstopbtn).asInstanceOf[Button]

	var serviceRunning = false

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.main)

		singleBtn.setOnClickListener(this);
		startstopBtn.setOnClickListener(this);

		registerReceiver(new BroadcastReceiver() {
			override def onReceive(ctx : Context, i : Intent) {
				val l = i.getParcelableExtra(AprsService.LOCATION).asInstanceOf[Location]
				onLocationChanged(l)
			}
		}, new IntentFilter(AprsService.UPDATE))
	}

	override def onDestroy() {
		super.onDestroy()
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

	def setupButtons() {
		singleBtn.setEnabled(!serviceRunning)
		if (serviceRunning) {
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
			serviceRunning = !serviceRunning
			if (serviceRunning) {
				startService(serviceIntent(AprsService.SERVICE))
			} else {
				stopService(serviceIntent(AprsService.SERVICE))
			}
			setupButtons()
		case _ =>
			status.setText(view.asInstanceOf[Button].getText)
		}
	}

}

