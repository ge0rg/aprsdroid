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
import _root_.android.view.{LayoutInflater, Menu, MenuItem, View, Window}
import _root_.android.view.View.OnClickListener
import _root_.android.widget.AdapterView
import _root_.android.widget.AdapterView.OnItemClickListener
import _root_.android.widget.Button
import _root_.android.widget.{ListView,SimpleCursorAdapter}
import _root_.android.widget.TextView
import _root_.android.widget.Toast
import _root_.java.util.Date

class APRSdroid extends LoadingListActivity with OnClickListener {
	val TAG = "APRSdroid"

	lazy val prefs = new PrefsWrapper(this)
	lazy val uihelper = new UIHelper(this, R.id.log, prefs)
	lazy val storage = StorageDatabase.open(this)
	lazy val postcursor = storage.getPosts("100")

	lazy val postlist = getListView()

	lazy val singleBtn = findViewById(R.id.singlebtn).asInstanceOf[Button]
	lazy val startstopBtn = findViewById(R.id.startstopbtn).asInstanceOf[Button]

	lazy val locReceiver = new LocationReceiver2[Cursor](load_cursor, replace_cursor, cancel_cursor)
	lazy val la = new SimpleCursorAdapter(this, R.layout.listitem, 
				null,
				Array("TSS", StorageDatabase.Post.STATUS, StorageDatabase.Post.MESSAGE),
				Array(R.id.listts, R.id.liststatus, R.id.listmessage))

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.main)

		Log.d(TAG, "starting " + getString(R.string.build_version))

		singleBtn.setOnClickListener(this);
		startstopBtn.setOnClickListener(this);

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

		if (!uihelper.checkConfig())
			return
		setTitle(getString(R.string.app_name) + ": " + prefs.getCallSsid())
		setupButtons(AprsService.running)

	}

	override def onPause() {
		super.onPause()
		unregisterReceiver(locReceiver)
	}

	override def onDestroy() {
		super.onDestroy()
		la.changeCursor(null)
	}

	override def onCreateOptionsMenu(menu : Menu) : Boolean = {
		getMenuInflater().inflate(R.menu.options, menu);
		true
	}
	override def onPrepareOptionsMenu(menu : Menu) = uihelper.onPrepareOptionsMenu(menu)

	def setupButtons(running : Boolean) {
		//singleBtn.setEnabled(!running)
		if (running) {
			startstopBtn.setText(R.string.stoplog)
		} else {
			startstopBtn.setText(R.string.startlog)
		}
	}

	override def onOptionsItemSelected(mi : MenuItem) : Boolean = {
		uihelper.optionsItemAction(mi)
	}

	override def onClick(view : View) {
		Log.d(TAG, "onClick: " + view + "/" + view.getId)

		view.getId match {
		case R.id.singlebtn =>
			uihelper.passcodeWarning(prefs.getCallsign(), prefs.getPasscode())
			startService(AprsService.intent(this, AprsService.SERVICE_ONCE))
		case R.id.startstopbtn =>
			val is_running = AprsService.running
			if (!is_running) {
				startService(AprsService.intent(this, AprsService.SERVICE))
			} else {
				stopService(AprsService.intent(this, AprsService.SERVICE))
			}
			setupButtons(!is_running)
		case _ =>
			//status.setText(view.asInstanceOf[Button].getText)
		}
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

	override def onStopLoading() {
		super.onStopLoading()
		setupButtons(AprsService.running)
	}

}
