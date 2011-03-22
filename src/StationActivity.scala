package org.aprsdroid.app

import _root_.android.app.ListActivity
import _root_.android.content._
import _root_.android.database.Cursor
import _root_.android.os.{Bundle, Handler}
import _root_.android.util.Log
import _root_.android.view.View
import _root_.android.widget.ListView

class StationActivity extends ListActivity {
	lazy val prefs = new PrefsWrapper(this)

	var targetcall = ""
	lazy val pla = getIntentPLA()

	def getIntentPLA() : PositionListAdapter = {
		val i = getIntent()
		val mycall = prefs.getCallSsid()
		if (i != null && i.getStringExtra("call") != null) {
			targetcall = i.getStringExtra("call")
			new PositionListAdapter(this, mycall, targetcall, PositionListAdapter.SSIDS)
		} else {
			new PositionListAdapter(this, mycall, mycall, PositionListAdapter.NEIGHBORS)
		}
	}

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.stationactivity)

		getListView().setOnCreateContextMenuListener(this);

		setListAdapter(pla)
	}

	override def onDestroy() {
		super.onDestroy()
		pla.onDestroy()
	}


	override def onListItemClick(l : ListView, v : View, position : Int, id : Long) {
		//super.onListItemClick(l, v, position, id)
		val c = getListView().getItemAtPosition(position).asInstanceOf[Cursor]
		val call = c.getString(StorageDatabase.Position.COLUMN_CALL)
		Log.d("StationActivity", "onListItemClick: %s".format(call))

		if (targetcall == call) {
			// click on own callssid
			startActivity(new Intent(this, classOf[MapAct]).putExtra("call", call));
		} else
			startActivity(new Intent(this, classOf[StationActivity]).putExtra("call", call));

	}
}
