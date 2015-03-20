package org.aprsdroid.app

import _root_.android.app.ListActivity
import _root_.android.content._
import _root_.android.database.Cursor
import _root_.android.net.Uri
import _root_.android.os.{Bundle, Handler}
import _root_.android.text.{ClipboardManager, Editable, TextWatcher}
import _root_.android.util.Log
import _root_.android.view.{ContextMenu, KeyEvent, Menu, MenuItem, View, Window}
import _root_.android.view.View.{OnClickListener, OnKeyListener}
import _root_.android.widget.{Button, EditText, ListView, Toast}
import _root_.android.widget.AdapterView.AdapterContextMenuInfo

class MessageActivity extends StationHelper(R.string.app_messages)
		with OnClickListener with OnKeyListener with TextWatcher {
	val TAG = "APRSdroid.Message"

	lazy val storage = StorageDatabase.open(this)
			
	lazy val mycall = prefs.getCallSsid()
	lazy val pla = new MessageListAdapter(this, prefs, mycall, targetcall)

	lazy val msginput = findView[EditText](R.id.msginput)
	lazy val msgsend = findView[Button](R.id.msgsend)

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.message_act)

		getListView().setOnCreateContextMenuListener(this);

		onStartLoading()
		setListAdapter(pla)

		msginput.addTextChangedListener(this)
		msginput.setOnKeyListener(this)
		msgsend.setOnClickListener(this)

		val message = getIntent().getStringExtra("message")
		if (message != null) {
			Log.d(TAG, "sending message to %s: %s".format(targetcall, message))
			sendMessage(message)
		}
	}

	override def onResume() {
		super.onResume()
		ServiceNotifier.instance.cancelMessage(this, targetcall)
	}

	override def onDestroy() {
		super.onDestroy()
		pla.onDestroy()
	}

	override def onPrepareOptionsMenu(menu : Menu) : Boolean = {
		menu.findItem(R.id.message).setVisible(false)
		true
	}

	// return message cursor for a given context menu
	def menuMessageCursor(menuInfo : ContextMenu.ContextMenuInfo) = {
		val i = menuInfo.asInstanceOf[AdapterContextMenuInfo]
		// a listview with a database backend gives out a cursor :D
		getListView().getItemAtPosition(i.position)
				.asInstanceOf[android.database.Cursor]
	}

	def messageAction(id : Int, c : Cursor) : Boolean = {
		import StorageDatabase.Message._
		val msg_id = c.getLong(/* COLUMN_ID */ 0)
		val msg_type = c.getInt(COLUMN_TYPE)
		id match {
		case R.id.copy =>
			getSystemService(Context.CLIPBOARD_SERVICE).asInstanceOf[ClipboardManager]
				.setText(c.getString(COLUMN_TEXT))
			true
		case R.id.abort =>
			if (msg_type == TYPE_OUT_NEW) {
				storage.updateMessageType(msg_id, TYPE_OUT_ABORTED)
				sendBroadcast(AprsService.MSG_PRIV_INTENT)
			}
			true
		case R.id.resend =>
			if (msg_type != TYPE_INCOMING) {
				val cv = new ContentValues()
				cv.put(TYPE, TYPE_OUT_NEW.asInstanceOf[java.lang.Integer])
				cv.put(RETRYCNT, 0.asInstanceOf[java.lang.Integer])
				cv.put(TS, System.currentTimeMillis.asInstanceOf[java.lang.Long])
				storage.updateMessage(msg_id, cv)
				sendBroadcast(AprsService.MSG_TX_PRIV_INTENT)
			}
			true
		case _ => false
		}
	}
	override def onCreateContextMenu(menu : ContextMenu, v : View,
			menuInfo : ContextMenu.ContextMenuInfo) {
		import StorageDatabase.Message._
		//super.onCreateContextMenu(menu, v, menuInfo)
		val c = menuMessageCursor(menuInfo)
		val msg_type = c.getInt(COLUMN_TYPE)
		val title_id = if (msg_type == TYPE_INCOMING) R.string.msg_from else R.string.msg_to
		getMenuInflater().inflate(R.menu.context_msg, menu)
		menu.setGroupVisible(R.id.msg_menu_out, msg_type != TYPE_INCOMING)
		menu.setHeaderTitle(getString(title_id, c.getString(COLUMN_CALL)))
	}

	override def onContextItemSelected(item : MenuItem) : Boolean = {
		Log.d(TAG, "menu for " + menuMessageCursor(item.getMenuInfo).getLong(0))
		messageAction(item.getItemId, menuMessageCursor(item.getMenuInfo))
	}


	// TextWatcher for msginput
	override def afterTextChanged(s : Editable) {
		msgsend.setEnabled(msginput.getText().length() > 0)
	}
	override def beforeTextChanged(s : CharSequence, start : Int, before : Int, count : Int) {
	}
	override def onTextChanged(s : CharSequence, start : Int, before : Int, count : Int) {
	}

	// react on "Return" key
	def onKey(v : View, kc : Int, ev : KeyEvent) = {
		if (ev.getAction() == KeyEvent.ACTION_DOWN && kc == KeyEvent.KEYCODE_ENTER) {
			sendMessage()
			true
		} else false
	}

	def sendMessage() {
		sendMessage(msginput.getText().toString())
	}
	def sendMessage(msg : String) {
		import StorageDatabase.Message._

		if (msg.length() == 0)
			return
		Log.d("MessageActivity", "sending " + msg)
		msginput.setText(null)

		val cv = new ContentValues()
		cv.put(TS, System.currentTimeMillis().asInstanceOf[java.lang.Long])
		cv.put(RETRYCNT, 0.asInstanceOf[java.lang.Integer])
		cv.put(CALL, targetcall)
		cv.put(MSGID, storage.createMsgId(targetcall).asInstanceOf[java.lang.Integer])
		cv.put(TYPE, TYPE_OUT_NEW.asInstanceOf[java.lang.Integer])
		cv.put(TEXT, msg)
		storage.addMessage(cv)
		// notify backend
		sendMessageBroadcast(targetcall, msg)
		// notify UI about new message
		sendBroadcast(AprsService.MSG_PRIV_INTENT)
		// if not connected, notify user about postponed message
		if (!AprsService.running)
			Toast.makeText(this, R.string.msg_stored_offline, Toast.LENGTH_SHORT).show()
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

}
