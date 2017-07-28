package org.aprsdroid.app

import _root_.android.app.ListActivity
import _root_.android.content._
import _root_.android.database.Cursor
import _root_.android.net.Uri
import _root_.android.os.{Bundle, Handler}
import _root_.android.util.Log
import _root_.android.view.{Menu, MenuItem, View, Window}
import _root_.android.view.View.OnClickListener
import _root_.android.widget.{ListView,SimpleCursorAdapter}

class StationActivity extends StationHelper(R.string.app_sta)
		with OnClickListener {
	lazy val storage = StorageDatabase.open(this)
	lazy val postlist = findViewById(R.id.postlist).asInstanceOf[ListView]
			
	lazy val mycall = prefs.getCallSsid()
	lazy val pla = new StationListAdapter(this, prefs, mycall, targetcall, StationListAdapter.SSIDS)
	lazy val la = new PostListAdapter(this)
	lazy val locReceiver = new LocationReceiver2[Cursor](load_cursor, replace_cursor, cancel_cursor)

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.stationactivity)

		getListView().setOnCreateContextMenuListener(this);

		onStartLoading()
		setListAdapter(pla)
		postlist.setAdapter(la)
		registerReceiver(locReceiver, new IntentFilter(AprsService.UPDATE))
		locReceiver.startTask(null)

		Array(R.id.map, R.id.qrzcom, R.id.aprsfi).foreach((id) => {
				findViewById(id).setOnClickListener(this)
			})
	}

	override def onDestroy() {
		super.onDestroy()
		pla.onDestroy()
		unregisterReceiver(locReceiver)
		la.changeCursor(null)
	}

	override def onPrepareOptionsMenu(menu : Menu) : Boolean = {
		menu.findItem(R.id.details).setVisible(false)
		menu.findItem(R.id.messagesclear).setVisible(false)
		true
	}

	override def onListItemClick(l : ListView, v : View, position : Int, id : Long) {
		//super.onListItemClick(l, v, position, id)
		val c = getListView().getItemAtPosition(position).asInstanceOf[Cursor]
		val call = c.getString(StorageDatabase.Station.COLUMN_CALL)
		Log.d("StationActivity", "onListItemClick: %s".format(call))

		if (targetcall == call) {
			// click on own callssid
			trackOnMap(call)
		} else {
			openDetails(call)
			finish()
		}
	}

	// button actions
	override def onClick(view : View) {
		callsignAction(view.getId, targetcall)
	}

	def load_cursor(i : Intent) = {
		val c = storage.getStaPosts(targetcall, "300")
		c.getCount()
		c
	}
	def replace_cursor(c : Cursor) {
		la.changeCursor(c)
		// do not call onStopLoading, StationListAdapter takes much longer
		//onStopLoading()
	}
	def cancel_cursor(c : Cursor) {
		c.close()
	}

}
