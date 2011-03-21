package org.aprsdroid.app

import _root_.android.app.ListActivity
import _root_.android.content._
import _root_.android.os.{Bundle, Handler}

class StationActivity extends ListActivity {
	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.stationactivity)

		setListAdapter(new PositionListAdapter(this, "DO1GL-7"))
	}
}
