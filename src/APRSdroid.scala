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
import _root_.android.widget.AdapterView
import _root_.android.widget.AdapterView.OnItemClickListener
import _root_.android.widget.SimpleCursorAdapter
import _root_.android.widget.TextView
import _root_.android.widget.Toast
import _root_.java.util.Date

class APRSdroid extends MainListActivity(R.id.log) {
	val TAG = "APRSdroid"

	lazy val storage = StorageDatabase.open(this)
	lazy val postcursor = storage.getPosts("100")

	lazy val postlist = getListView()

	lazy val locReceiver = new LocationReceiver2[Cursor](load_cursor, replace_cursor, cancel_cursor)
	lazy val la = new SimpleCursorAdapter(this, R.layout.listitem, 
				null,
				Array("TSS", StorageDatabase.Post.STATUS, StorageDatabase.Post.MESSAGE),
				Array(R.id.listts, R.id.liststatus, R.id.listmessage))

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.main)

		Log.d(TAG, "starting " + getString(R.string.build_version))

		onContentViewLoaded()

		onStartLoading()

		la.setViewBinder(new PostViewBinder())
		la.setFilterQueryProvider(storage.getPostFilter("100"))

		postlist.setAdapter(la)
		postlist.setTextFilterEnabled(true)
		postlist.setOnItemClickListener(new OnItemClickListener() {
			override def onItemClick(parent : AdapterView[_], view : View, position : Int, id : Long) {
				// When clicked, show a toast with the TextView text
				val (ts, status, message) = storage.getSinglePost("_ID = ?", Array(id.toString()))
				Log.d(TAG, "onItemClick: %s: %s".format(status, message))
				if (status != null) {
					// extract call sign
					val call = message.split(">")(0)
					startActivity(new Intent(APRSdroid.this, classOf[StationActivity]).putExtra("call", call));
				}
			}
		});
	}

	override def onResume() {
		super.onResume()
		registerReceiver(locReceiver, new IntentFilter(AprsService.UPDATE))
		locReceiver.startTask(null)

		setTitle(getString(R.string.app_name) + ": " + prefs.getCallSsid())
	}

	override def onPause() {
		super.onPause()
		unregisterReceiver(locReceiver)
	}

	override def onDestroy() {
		super.onDestroy()
		la.changeCursor(null)
	}

	def load_cursor(i : Intent) = {
		val c = storage.getPosts("100")
		c.getCount()
		c
	}
	def replace_cursor(c : Cursor) {
		la.changeCursor(c)
		onStopLoading()
	}
	def cancel_cursor(c : Cursor) {
		c.close()
	}

}
