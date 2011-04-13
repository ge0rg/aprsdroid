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

class StationActivity extends ListActivity with OnClickListener {
	lazy val prefs = new PrefsWrapper(this)
	lazy val uihelper = new UIHelper(this, -1, prefs)

	lazy val targetcall = getIntent().getStringExtra("call")

	lazy val storage = StorageDatabase.open(this)
	lazy val postcursor = storage.getStaPosts(targetcall, "100")

	lazy val postlist = findViewById(R.id.postlist).asInstanceOf[ListView]
			
	lazy val mycall = prefs.getCallSsid()
	lazy val pla = new PositionListAdapter(this, prefs, mycall, targetcall, PositionListAdapter.SSIDS)

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS)
		setContentView(R.layout.stationactivity)
		setProgressBarIndeterminateVisibility(true)

		getListView().setOnCreateContextMenuListener(this);

		setListAdapter(pla)
		startManagingCursor(postcursor)
		val la = new SimpleCursorAdapter(this, R.layout.listitem, 
				postcursor,
				Array("TSS", StorageDatabase.Post.STATUS, StorageDatabase.Post.MESSAGE),
				Array(R.id.listts, R.id.liststatus, R.id.listmessage))
		la.setViewBinder(new PostViewBinder())
		postlist.setAdapter(la)

		Array(R.id.mapbutton, R.id.qrzcombutton, R.id.aprsfibutton).foreach((id) => {
				findViewById(id).setOnClickListener(this)
			})
	}

	override def onDestroy() {
		super.onDestroy()
		pla.onDestroy()
	}

	override def onCreateOptionsMenu(menu : Menu) : Boolean = {
		getMenuInflater().inflate(R.menu.options, menu);
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
		Log.d("StationActivity", "onListItemClick: %s".format(call))

		if (targetcall == call) {
			// click on own callssid
			uihelper.trackOnMap(call)
		} else {
			startActivity(new Intent(this, classOf[StationActivity]).putExtra("call", call));
			finish()
		}
	}

	// button actions
	override def onClick(view : View) {
		view.getId match {
		case R.id.mapbutton =>
			uihelper.trackOnMap(targetcall)
		case R.id.aprsfibutton =>
			val url = "http://aprs.fi/?call=%s".format(targetcall)
			startActivity(new Intent(Intent.ACTION_VIEW,
				Uri.parse(url)))
		case R.id.qrzcombutton =>
			val url = "http://qrz.com/db/%s".format(targetcall.split("[- ]+")(0))
			startActivity(new Intent(Intent.ACTION_VIEW,
				Uri.parse(url)))
		case _ =>
			//status.setText(view.asInstanceOf[Button].getText)
		}
	}
}
