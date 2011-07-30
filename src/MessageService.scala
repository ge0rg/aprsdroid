package org.aprsdroid.app

import _root_.android.content.{BroadcastReceiver, ContentValues, Context, Intent}
import _root_.android.util.Log
import _root_.android.os.Handler

import _root_.net.ab0oo.aprs.parser._

class MessageService(s : AprsService) {
	val TAG = "APRSdroid.MsgService"

	val NUM_OF_RETRIES = 7

	def createMessageNotifier() = new BroadcastReceiver() {
		override def onReceive(ctx : Context, i : Intent) {
			sendPendingMessages()
		}
	}

	def handleMessage(ts : Long, ap : APRSPacket, msg : MessagePacket) {
		val callssid = s.prefs.getCallSsid()
		if (msg.getTargetCallsign() == callssid) {
			if (msg.isAck() || msg.isRej()) {
				val new_type = if (msg.isAck())
					StorageDatabase.Message.TYPE_OUT_ACKED
				else
					StorageDatabase.Message.TYPE_OUT_REJECTED
				s.db.updateMessageAcked(ap.getSourceCall(), msg.getMessageNumber(), new_type)
			} else {
				s.db.addMessage(ts, ap.getSourceCall(), msg)
				if (msg.getMessageNumber() != "") {
					// we need to send an ack
					val ack = AprsPacket.formatMessage(callssid, s.appVersion(), ap.getSourceCall(), "ack", msg.getMessageNumber())
					val status = s.poster.update(ack)
					s.addPost(StorageDatabase.Post.TYPE_POST, status, ack.toString)
				}
				ServiceNotifier.instance.notifyMessage(s, s.prefs, 
					ap.getSourceCall(), msg.getMessageBody())
			}
			s.sendBroadcast(new Intent(AprsService.MESSAGE).putExtra(AprsService.STATUS, ap.toString))
		}
	}

	def canSendMsg(ts : Long, retrycnt : Int) : Boolean = {
		if (retrycnt == 0)
			true
		else {
			//val delta = 30000*scala.math.pow(2, retrycnt-1).toLong
			val delta = 30000 * (1 << (retrycnt - 1))
			(ts + delta < System.currentTimeMillis)
		}
	}

	def sendPendingMessages() {
		import StorageDatabase.Message._

		val callssid = s.prefs.getCallSsid()

		val c = s.db.getPendingMessages(NUM_OF_RETRIES)
		//Log.d(TAG, "sendPendingMessages")
		c.moveToFirst()
		while (!c.isAfterLast()) {
			val ts = c.getLong(COLUMN_TS)
			val retrycnt = c.getInt(COLUMN_RETRYCNT)
			val call = c.getString(COLUMN_CALL)
			val msgid = c.getString(COLUMN_MSGID)
			val msgtype = c.getInt(COLUMN_TYPE)
			val text = c.getString(COLUMN_TEXT)
			Log.d(TAG, "pending message: %d/%d ->%s '%s'".format(retrycnt, NUM_OF_RETRIES, call, text))
			if (retrycnt < NUM_OF_RETRIES && canSendMsg(ts, retrycnt)) {
				val msg = AprsPacket.formatMessage(callssid, s.appVersion(), call, text, msgid)
				val status = s.poster.update(msg)
				s.addPost(StorageDatabase.Post.TYPE_POST, status, msg.toString)
				val cv = new ContentValues()
				cv.put(RETRYCNT, (retrycnt + 1).asInstanceOf[java.lang.Integer])
				cv.put(TS, System.currentTimeMillis.asInstanceOf[java.lang.Long])
				// XXX: do not ack until acked
				s.db.updateMessage(c.getLong(/* COLUMN_ID */ 0), cv)
				s.sendBroadcast(new Intent(AprsService.MESSAGE).putExtra(AprsService.STATUS, msg.toString))
			}
			c.moveToNext()
		}
		c.close()
	}
}
