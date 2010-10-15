package de.duenndns.aprsdroid

import _root_.android.os.Bundle
import _root_.com.google.android.maps.{MapActivity, MapView}

class MapAct extends MapActivity {
	val TAG = "MapAct"

	lazy val mapview = findViewById(R.id.mapview).asInstanceOf[MapView]

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.mapview)
		mapview.setBuiltInZoomControls(true)
	}

	override def isRouteDisplayed() = false
}
