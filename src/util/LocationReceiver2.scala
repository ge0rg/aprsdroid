package org.aprsdroid.app

import _root_.android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import _root_.android.os.Handler
import _root_.android.util.Log


class LocationReceiver2[Result](bg_task : (Intent) => Result,
				finish_task : (Result) => Unit,
				cancel_task : (Result) => Unit)
		extends BroadcastReceiver {

	var pending = 0

	def startTask(i : Intent) {
		pending += 1
		if (pending == 1)
			new LRAsync(i).execute(null)
	}

	override def onReceive(ctx : Context, i : Intent) {
		startTask(i)
	}

	class LRAsync(val i : Intent) extends MyAsyncTask[Unit, Result] {
		override def doInBackground1(params : Array[String]) = {
			val r = bg_task(i)
			// here we cheat and call cancel from the bg thread, not from UI
			if (isCancelled())
				cancel_task(r)
			r
		}

		override def onPostExecute(result : Result) {
			finish_task(result)
			if (pending > 1) {
				// something happened, we need to rerun
				Log.d("LocationReceiver2", "rerunning...")
				pending = 0
				startTask(i)
			} else
				pending = 0
		}
	}
}
