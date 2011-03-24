package org.aprsdroid.app

import _root_.android.app.ListActivity
import _root_.android.content._
import _root_.android.database.Cursor
import _root_.android.os.{Bundle, Handler}
import _root_.android.util.Log
import _root_.android.view.{Menu, MenuItem, View}
import _root_.android.widget.ListView

class HubActivity extends ListActivity {
	lazy val prefs = new PrefsWrapper(this)
	lazy val uihelper = new UIHelper(this, R.id.hub, prefs)

	val mycall = prefs.getCallSsid()
	lazy val pla = new PositionListAdapter(this, mycall, mycall, PositionListAdapter.NEIGHBORS)

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.hubactivity)

		getListView().setOnCreateContextMenuListener(this);

		setListAdapter(pla)
	}

	override def onDestroy() {
		super.onDestroy()
		pla.onDestroy()
	}

	override def onCreateOptionsMenu(menu : Menu) : Boolean = {
		getMenuInflater().inflate(R.menu.options_map, menu);
		true
	}

	override def onPrepareOptionsMenu(menu : Menu) = uihelper.onPrepareOptionsMenu(menu)

	override def onOptionsItemSelected(mi : MenuItem) : Boolean = {
		uihelper.optionsItemAction(mi)
	}


	override def onListItemClick(l : ListView, v : View, position : Int, id : Long) {
		//super.onListItemClick(l, v, position, id)
		val c = getListView().getItemAtPosition(position).asInstanceOf[Cursor]
		val call = c.getString(StorageDatabase.Position.COLUMN_CALL)
		Log.d("HubActivity", "onListItemClick: %s".format(call))

		startActivity(new Intent(this, classOf[StationActivity]).putExtra("call", call));
	}
}
