package org.aprsdroid.app

import _root_.android.app.ListActivity
import _root_.android.content._
import _root_.android.database.Cursor
import _root_.android.os.{Bundle, Handler}
import _root_.android.util.Log
import _root_.android.view.{Menu, MenuItem, View, Window}
import _root_.android.view.View.OnClickListener
import _root_.android.widget.Button
import _root_.android.widget.ListView

class HubActivity extends LoadingListActivity with OnClickListener {
	lazy val prefs = new PrefsWrapper(this)
	lazy val uihelper = new UIHelper(this, R.id.hub, prefs)

	lazy val singleBtn = findViewById(R.id.singlebtn).asInstanceOf[Button]
	lazy val startstopBtn = findViewById(R.id.startstopbtn).asInstanceOf[Button]

	lazy val mycall = prefs.getCallSsid()
	lazy val pla = new PositionListAdapter(this, prefs, mycall, mycall, PositionListAdapter.NEIGHBORS)

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.main)

		singleBtn.setOnClickListener(this);
		startstopBtn.setOnClickListener(this);

		getListView().setOnCreateContextMenuListener(this);

		onStartLoading()
		setListAdapter(pla)
	}

	override def onResume() {
		super.onResume()
		setupButtons(AprsService.running)
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

	def setupButtons(running : Boolean) {
		//singleBtn.setEnabled(!running)
		if (running) {
			startstopBtn.setText(R.string.stoplog)
		} else {
			startstopBtn.setText(R.string.startlog)
		}
	}

	override def onClick(view : View) {
		view.getId match {
		case R.id.singlebtn =>
			uihelper.passcodeWarning(prefs.getCallsign(), prefs.getPasscode())
			startService(AprsService.intent(this, AprsService.SERVICE_ONCE))
			setupButtons(true)
		case R.id.startstopbtn =>
			val is_running = AprsService.running
			if (!is_running) {
				startService(AprsService.intent(this, AprsService.SERVICE))
			} else {
				stopService(AprsService.intent(this, AprsService.SERVICE))
			}
			setupButtons(!is_running)
		}
	}


	override def onListItemClick(l : ListView, v : View, position : Int, id : Long) {
		//super.onListItemClick(l, v, position, id)
		val c = getListView().getItemAtPosition(position).asInstanceOf[Cursor]
		val call = c.getString(StorageDatabase.Position.COLUMN_CALL)
		Log.d("HubActivity", "onListItemClick: %s".format(call))

		startActivity(new Intent(this, classOf[StationActivity]).putExtra("call", call));
	}

	override def onStopLoading() {
		super.onStopLoading()
		setupButtons(AprsService.running)
	}

}
