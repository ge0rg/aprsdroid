package org.aprsdroid.app

import _root_.android.app.AlertDialog
import _root_.android.app.ListActivity
import _root_.android.content._
import _root_.android.database.Cursor
import _root_.android.os.{Bundle, Handler}
import _root_.android.util.Log
import _root_.android.view.{ContextMenu, LayoutInflater, Menu, MenuItem, View}
import _root_.android.view.View.OnClickListener
import _root_.android.widget.{Button, EditText, ListView}

class ConversationsActivity extends LoadingListActivity
		with OnClickListener {
	val TAG = "APRSdroid.Conversations"

	menu_id = R.id.conversations

	lazy val mycall = prefs.getCallSsid()
	lazy val pla = new ConversationListAdapter(this, prefs)

	lazy val newConversationBtn = findViewById(R.id.new_conversation).asInstanceOf[Button]

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.conversations)

		registerForContextMenu(getListView())
		newConversationBtn.setOnClickListener(this);

		getListView().setOnCreateContextMenuListener(this);

		onStartLoading()
		setListAdapter(pla)
		getListView().setTextFilterEnabled(true)
	}

	override def onDestroy() {
		super.onDestroy()
		pla.onDestroy()
	}

	override def onCreateOptionsMenu(menu : Menu) : Boolean = {
		getMenuInflater().inflate(R.menu.options, menu);
		true
	}

	override def onListItemClick(l : ListView, v : View, position : Int, id : Long) {
		//super.onListItemClick(l, v, position, id)
		val c = getListView().getItemAtPosition(position).asInstanceOf[Cursor]
		val call = c.getString(StorageDatabase.Message.COLUMN_CALL)
		openMessaging(call)
	}

	override def onClick(view : View) {
		view.getId match {
		case R.id.new_conversation =>
			newConversation()
		}
	}


	def newConversation() {
			val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE)
					.asInstanceOf[LayoutInflater]
			val nm_view = inflater.inflate(R.layout.new_message_view, null, false)
			val nm_call = nm_view.findViewById(R.id.callsign).asInstanceOf[EditText]
			val nm_text = nm_view.findViewById(R.id.message).asInstanceOf[EditText]
			new AlertDialog.Builder(this).setTitle(getString(R.string.msg_send_new))
				.setView(nm_view)
				//.setIcon(android.R.drawable.ic_dialog_info)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					override def onClick(d : DialogInterface, which : Int) {
						which match {
							case DialogInterface.BUTTON_POSITIVE =>
							openMessageSend(nm_call.getText().toString(),
									nm_text.getText().toString())
							case _ =>
							finish()
						}
					}})
				.setNegativeButton(android.R.string.cancel, null)
				.create.show
	}
}
