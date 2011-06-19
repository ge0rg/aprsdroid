package org.aprsdroid.app

import _root_.android.app.Activity
import _root_.android.content._
import _root_.android.database.Cursor
import _root_.android.os.{AsyncTask, Bundle, Handler}
import _root_.android.text.format.DateUtils
import _root_.android.util.Log
import _root_.android.view.View
import _root_.android.widget.{SimpleCursorAdapter, TextView}

object MessageListAdapter {
	import StorageDatabase.Message._
	val LIST_FROM = Array("TSS", CALL, TEXT)
	val LIST_TO = Array(R.id.listts, R.id.liststatus, R.id.listmessage)

	// null, incoming, out-new, out-acked, out-rejected
	val COLORS = Array(0, 0xff8080b0, 0xff80a080, 0xff30b030, 0xffb03030)
}

class MessageListAdapter(context : Context, prefs : PrefsWrapper,
	mycall : String, targetcall : String)
		extends SimpleCursorAdapter(context, R.layout.listitem, null, MessageListAdapter.LIST_FROM, MessageListAdapter.LIST_TO) {

	lazy val storage = StorageDatabase.open(context)

	reload()

	lazy val locReceiver = new LocationReceiver2(load_cursor,
		replace_cursor, cancel_cursor)

	context.registerReceiver(locReceiver, new IntentFilter(AprsService.MESSAGE))

	override def bindView(view : View, context : Context, cursor : Cursor) {
		import StorageDatabase.Message._
		val msgtype = cursor.getInt(COLUMN_TYPE)
		val retrycnt = cursor.getInt(COLUMN_RETRYCNT)
		view.findViewById(R.id.listmessage).asInstanceOf[TextView]
			.setTextColor(MessageListAdapter.COLORS(msgtype))
		val statusview = view.findViewById(R.id.liststatus).asInstanceOf[TextView]
		statusview.setTextColor(MessageListAdapter.COLORS(msgtype))
		super.bindView(view, context, cursor)
		msgtype match {
		case TYPE_INCOMING =>
			statusview.setText(targetcall)
		case TYPE_OUT_NEW =>
			statusview.setText("%s %d/5".format(mycall, retrycnt))
		case TYPE_OUT_ACKED =>
			//statusview.setText("%s ack #%d".format(mycall, retrycnt))
			statusview.setText(mycall)
		case TYPE_OUT_REJECTED =>
			statusview.setText("%s rej #%d".format(mycall, retrycnt))
		}
	}

	def load_cursor(i : Intent) = {
		val c = storage.getMessages(targetcall)
		Benchmark("getCount") { c.getCount() }
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
