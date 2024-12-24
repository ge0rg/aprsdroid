package org.aprsdroid.app

import _root_.android.app.AlertDialog
import _root_.android.content._
import _root_.android.content.pm.PackageInfo;
import _root_.android.database.Cursor
import _root_.android.location._
import _root_.android.os.{Bundle, Handler}
import _root_.android.preference.PreferenceManager
import _root_.java.text.SimpleDateFormat
import _root_.android.util.Log
import _root_.android.view.View
import _root_.android.widget.ListView
import _root_.android.widget.SimpleCursorAdapter
import _root_.android.widget.TextView
import _root_.android.widget.Toast
import _root_.java.util.Date

class LogActivity extends MainListActivity("log", R.id.log) {
	val TAG = "APRSdroid.Log"

	lazy val storage = StorageDatabase.open(this)
	lazy val postcursor = storage.getPosts("300")

	lazy val postlist = getListView()

	lazy val locReceiver = new LocationReceiver2[Cursor](load_cursor, replace_cursor, cancel_cursor)
	lazy val la = new PostListAdapter(this)

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.main)

		Log.d(TAG, "starting " + getString(R.string.build_version))

		onContentViewLoaded()

		onStartLoading()

		la.setFilterQueryProvider(storage.getPostFilter("300"))

		postlist.setAdapter(la)
		postlist.setTextFilterEnabled(true)
	}

	override def onResume() {
		super.onResume()
		registerReceiver(locReceiver, new IntentFilter(AprsService.UPDATE))
		locReceiver.startTask(null)

		postlist.requestFocus()
	}

	override def onPause() {
		super.onPause()
		unregisterReceiver(locReceiver)
	}

	override def onDestroy() {
		super.onDestroy()
		la.changeCursor(null)
	}

	override def onListItemClick(l : ListView, v : View, position : Int, id : Long) {
		import StorageDatabase.Post._
		//super.onListItemClick(l, v, position, id)
		val c = getListView().getItemAtPosition(position).asInstanceOf[Cursor]
		val t = c.getInt(COLUMN_TYPE)
		if (t != TYPE_POST && t != TYPE_INCMG && t != TYPE_DIGI)
			return
		val call = c.getString(COLUMN_MESSAGE).split(">")(0)
		Log.d(TAG, "onListItemClick: %s".format(call))
		openDetails(call)
	}

	def load_cursor(i : Intent) = {
		val c = storage.getPosts("300")
		c.getCount()
		c
	}
	def replace_cursor(c : Cursor) {
		if (!getListView().hasTextFilter())
			la.changeCursor(c)
		onStopLoading()
	}
	def cancel_cursor(c : Cursor) {
		c.close()
	}

}
