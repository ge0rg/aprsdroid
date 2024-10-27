package org.aprsdroid.app

import _root_.android.content.{BroadcastReceiver, ContentValues, Context, Intent}
import _root_.android.util.Log
import _root_.android.os.Handler

import _root_.net.ab0oo.aprs.parser._

class MessageService(s : AprsService) {
	val TAG = "APRSdroid.MsgService"

	val NUM_OF_RETRIES = s.prefs.getStringInt("p.messaging", 7)

	val RETRY_INTERVAL = s.prefs.getStringInt("p.retry", 30)


	val pendingSender = new Runnable() { override def run() { sendPendingMessages() } }

	def createMessageNotifier() = new BroadcastReceiver() {
		override def onReceive(ctx : Context, i : Intent) {
			sendPendingMessages()
		}
	}

	def storeNotifyMessage(ts : Long, srccall : String, msg : MessagePacket) {
		val is_new = s.db.addMessage(ts, srccall, msg)
		if (is_new)
			ServiceNotifier.instance.notifyMessage(s, s.prefs, srccall, msg.getMessageBody())

		s.sendBroadcast(new Intent(AprsService.MESSAGE)
			.putExtra(AprsService.SOURCE, srccall)
			.putExtra(AprsService.DEST, msg.getTargetCallsign())
			.putExtra(AprsService.BODY, msg.getMessageBody())
			)
	}

	def handleMessage(ts : Long, ap : APRSPacket, msg : MessagePacket) {
		val callssid = s.prefs.getCallSsid()
		if (msg.getTargetCallsign().equalsIgnoreCase(callssid)) {
			if (msg.isAck() || msg.isRej()) {
				val new_type = if (msg.isAck())
					StorageDatabase.Message.TYPE_OUT_ACKED
				else
					StorageDatabase.Message.TYPE_OUT_REJECTED
				s.db.updateMessageAcked(ap.getSourceCall(), msg.getMessageNumber(), new_type)
				s.sendBroadcast(AprsService.MSG_PRIV_INTENT)
			} else {
				storeNotifyMessage(ts, ap.getSourceCall(), msg)
				if (msg.getMessageNumber() != "") {
					// we need to send an ack
					val ack = s.newPacket(new MessagePacket(ap.getSourceCall(), "ack", msg.getMessageNumber()))
					s.sendPacket(ack)
				}
			}
		} else if (msg.getTargetCallsign().split("-")(0).equalsIgnoreCase(
				s.prefs.getCallsign()) && !msg.isAck() && !msg.isRej()) {
			// incoming message for a different ssid of our callsign
			if (ap.getSourceCall().equalsIgnoreCase(callssid))
				return; // ignore messages from self, fix #283
			Log.d(TAG, "incoming message for " + msg.getTargetCallsign())
			storeNotifyMessage(ts, ap.getSourceCall(), msg)
		}
	}

	// return 2^n * 30s, at most 32min
	def getRetryDelayMS(retrycnt : Int) = (RETRY_INTERVAL * 1000) * (1 << math.min(retrycnt - 1, NUM_OF_RETRIES))


	def scheduleNextSend(delay : Long) {
		// add some time to prevent fast looping
		Log.d(TAG, "scheduling TX in " + (delay+999)/1000 + "s")
		s.handler.postDelayed(pendingSender, (delay+999)/1000*1000)
	}

	// called when the service is terminated, we have to clean up timers
	def stop() {
		s.handler.removeCallbacks(pendingSender)
	}

	def sendPendingMessages() {
		import StorageDatabase.Message._

		s.handler.removeCallbacks(pendingSender)

		// when to schedule next send round
		var next_run = Long.MaxValue

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
			val t_send = ts + getRetryDelayMS(retrycnt) - System.currentTimeMillis()
			Log.d(TAG, "pending message: %d/%d (%ds) ->%s '%s'".format(retrycnt, NUM_OF_RETRIES,
				t_send/1000, call, text))
			if (retrycnt == NUM_OF_RETRIES && t_send <= 0) {
				// this message timed out
				s.db.updateMessageType(c.getLong(/* COLUMN_ID */ 0), TYPE_OUT_ABORTED)
				s.sendBroadcast(AprsService.MSG_PRIV_INTENT)
			} else if (retrycnt < NUM_OF_RETRIES && t_send <= 0) {
				// this message needs to be transmitted
				val msg = s.newPacket(new MessagePacket(call, text, msgid))
				s.sendPacket(msg)
				val cv = new ContentValues()
				cv.put(RETRYCNT, (retrycnt + 1).asInstanceOf[java.lang.Integer])
				cv.put(TS, System.currentTimeMillis.asInstanceOf[java.lang.Long])
				// XXX: do not ack until acked
				s.db.updateMessage(c.getLong(/* COLUMN_ID */ 0), cv)
				s.sendBroadcast(AprsService.MSG_PRIV_INTENT)
				// schedule potential re-transmission
				next_run = math.min(next_run, getRetryDelayMS(retrycnt + 1))
			} else if (retrycnt < NUM_OF_RETRIES) {
				// schedule transmission
				next_run = math.min(next_run, t_send)
			}
			c.moveToNext()
		}
		c.close()

		// reschedule transmission
		if (next_run != Long.MaxValue)
			scheduleNextSend(next_run)
	}
}
