package org.aprsdroid.app

import _root_.android.app.ListActivity
import _root_.android.content._
import _root_.android.database.Cursor
import _root_.android.os.{Bundle, Handler}
import _root_.android.util.Log
import _root_.android.view.View
import _root_.android.widget.ListView

class HubActivity extends MainListActivity("hub", R.id.hub) {
	val TAG = "APRSdroid.Hub"

	lazy val mycall = prefs.getCallSsid()
	lazy val pla = new StationListAdapter(this, prefs, mycall, mycall, StationListAdapter.NEIGHBORS)

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.main)

		onContentViewLoaded()

		getListView().setOnCreateContextMenuListener(this);

		onStartLoading()
		setListAdapter(pla)
		getListView().setTextFilterEnabled(true)
	}

	override def onDestroy() {
		super.onDestroy()
		pla.onDestroy()
	}


	override def onListItemClick(l : ListView, v : View, position : Int, id : Long) {
		//super.onListItemClick(l, v, position, id)
		val c = getListView().getItemAtPosition(position).asInstanceOf[Cursor]
		val call = c.getString(StorageDatabase.Station.COLUMN_CALL)
		openDetails(call)
	}

}
