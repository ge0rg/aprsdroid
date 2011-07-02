package org.aprsdroid.app

import _root_.android.app.Activity
import _root_.android.content._
import _root_.android.database.Cursor
import _root_.android.os.{AsyncTask, Bundle, Handler}
import _root_.android.text.format.DateUtils
import _root_.android.util.Log
import _root_.android.view.View
import _root_.android.widget.{SimpleCursorAdapter, TextView}

object ConversationListAdapter {
	import StorageDatabase.Message._
	val LIST_FROM = Array(CALL, TEXT)
	val LIST_TO = Array(R.id.call, R.id.message)

	// null, incoming, out-new, out-acked, out-rejected
	val COLORS = Array(0, 0xff8080b0, 0xff80a080, 0xff30b030, 0xffb03030)
}

class ConversationListAdapter(context : Context, prefs : PrefsWrapper)
		extends SimpleCursorAdapter(context, R.layout.conversationview, null,
			ConversationListAdapter.LIST_FROM, ConversationListAdapter.LIST_TO) {

	lazy val storage = StorageDatabase.open(context)

	reload()

	lazy val locReceiver = new LocationReceiver2(load_cursor,
		replace_cursor, cancel_cursor)

	context.registerReceiver(locReceiver, new IntentFilter(AprsService.MESSAGE))

	override def bindView(view : View, context : Context, cursor : Cursor) {
		import StorageDatabase.Message._
		val ts = cursor.getLong(COLUMN_TS)
		val msgtype = cursor.getInt(COLUMN_TYPE)
		view.findViewById(R.id.message).asInstanceOf[TextView]
			.setTextColor(MessageListAdapter.COLORS(msgtype))
		val age = DateUtils.getRelativeTimeSpanString(context, ts)
		view.findViewById(R.id.ts).asInstanceOf[TextView].setText(age)
		super.bindView(view, context, cursor)
	}

	def load_cursor(i : Intent) = {
		val c = storage.getConversations()
		c.getCount()
		c
	}

	def replace_cursor(c : Cursor) {
		changeCursor(c)
		context.asInstanceOf[LoadingIndicator].onStopLoading()
	}
	def cancel_cursor(c : Cursor) {
		c.close()
	}

	def reload() {
		locReceiver.startTask(null)
	}

	def onDestroy() {
		context.unregisterReceiver(locReceiver)
		changeCursor(null)
	}
}
