package org.aprsdroid.app

import _root_.android.app.ListActivity
import _root_.android.content._
import _root_.android.database.Cursor
import _root_.android.net.Uri
import _root_.android.os.{Bundle, Handler}
import _root_.android.text.{Editable, TextWatcher}
import _root_.android.util.Log
import _root_.android.view.{Menu, MenuItem, View, Window}
import _root_.android.view.View.OnClickListener
import _root_.android.widget.{Button, EditText, ListView}

class MessageActivity extends LoadingListActivity
		with OnClickListener with TextWatcher {
	val TAG = "APRSdroid.Message"
	lazy val targetcall = getIntent().getStringExtra("call")

	lazy val storage = StorageDatabase.open(this)
			
	lazy val mycall = prefs.getCallSsid()
	lazy val pla = new MessageListAdapter(this, prefs, mycall, targetcall)
	lazy val locReceiver = new LocationReceiver2[Cursor](load_cursor, replace_cursor, cancel_cursor)

	lazy val msginput = findView[EditText](R.id.msginput)
	lazy val msgsend = findView[Button](R.id.msgsend)

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.message_act)

		getListView().setOnCreateContextMenuListener(this);

		onStartLoading()
		setListAdapter(pla)
		registerReceiver(locReceiver, new IntentFilter(AprsService.UPDATE))
		locReceiver.startTask(null)

		msginput.addTextChangedListener(this)
		msgsend.setOnClickListener(this)

		setTitle(getString(R.string.app_sta) + ": " + targetcall)
	}

	override def onDestroy() {
		super.onDestroy()
		pla.onDestroy()
		unregisterReceiver(locReceiver)
	}

	override def onCreateOptionsMenu(menu : Menu) : Boolean = {
		getMenuInflater().inflate(R.menu.options, menu);
		true
	}

	override def onListItemClick(l : ListView, v : View, position : Int, id : Long) {
		//super.onListItemClick(l, v, position, id)
		val c = getListView().getItemAtPosition(position).asInstanceOf[Cursor]
		val call = c.getString(StorageDatabase.Position.COLUMN_CALL)
		Log.d("MessageActivity", "onListItemClick: %s".format(call))

		if (targetcall == call) {
			// click on own callssid
			trackOnMap(call)
		} else {
			openDetails(call)
			finish()
		}
	}

	// TextWatcher for msginput
	override def afterTextChanged(s : Editable) {
		msgsend.setEnabled(msginput.getText().length() > 0)
	}
	override def beforeTextChanged(s : CharSequence, start : Int, before : Int, count : Int) {
	}
	override def onTextChanged(s : CharSequence, start : Int, before : Int, count : Int) {
	}

	def sendMessage() {
		val msg = msginput.getText().toString()
		if (msg.length() == 0)
			return
		Log.d("MessageActivity", "sending " + msg)
		msginput.setText(null)
	}

	// button actions
	override def onClick(view : View) {
		Log.d(TAG, "onClick: " + view.getId)
		view.getId match {
		case R.id.msgsend =>
			sendMessage()
			true
		case _ => false
		}
	}

	def load_cursor(i : Intent) = {
		val c = storage.getStaPosts(targetcall, "100")
		c.getCount()
		c
	}
	def replace_cursor(c : Cursor) {
		// do not call onStopLoading, PositionListAdapter takes much longer
		//onStopLoading()
	}
	def cancel_cursor(c : Cursor) {
		c.close()
	}

}
