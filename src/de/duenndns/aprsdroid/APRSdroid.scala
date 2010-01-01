package de.duenndns.aprsdroid

import _root_.android.app.Activity
import _root_.android.content.Context
import _root_.android.location._
import _root_.android.os.Bundle
import _root_.android.util.Log
import _root_.android.view.View
import _root_.android.view.View.OnClickListener
import _root_.android.widget.Button
import _root_.android.widget.TextView

class APRSdroid extends Activity with LocationListener with OnClickListener {
	val UPDATE_TIME = 10000 // 10k ms = 10s
	val UPDATE_DIST = 10 // 10m
	var locMan : LocationManager = null
        lazy val lat : TextView = findViewById(R.id.lat).asInstanceOf[TextView]
        lazy val lon : TextView = findViewById(R.id.lon).asInstanceOf[TextView]
        lazy val status : TextView = findViewById(R.id.status).asInstanceOf[TextView]

	val TAG = "APRSdroid"

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.main)
		locMan = getSystemService(Context.LOCATION_SERVICE).asInstanceOf[LocationManager]
		locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER,
			UPDATE_TIME, UPDATE_DIST, this)

                findViewById(R.id.startbtn).asInstanceOf[Button].setOnClickListener(this);
                findViewById(R.id.stopbtn).asInstanceOf[Button].setOnClickListener(this);
                findViewById(R.id.singlebtn).asInstanceOf[Button].setOnClickListener(this);
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
	override def onClick(view : View) {
		Log.d(TAG, "onClick: " + view + "/" + view.getId)
                status.setText(view.asInstanceOf[Button].getText)
	}

}

