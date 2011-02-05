package org.aprsdroid.app

import _root_.android.app.Activity
import _root_.android.os.{Bundle, Handler}

class StationActivity extends Activity {
	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.stationactivity)
	}

}
