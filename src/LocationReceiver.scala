package de.duenndns.aprsdroid

import _root_.android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import _root_.android.os.Handler


class LocationReceiver(handler : Handler, callback : () => Unit) extends BroadcastReceiver {
	lazy val runnable = new Runnable() {
		override def run() {
			callback()
		}
	}

	override def onReceive(ctx : Context, i : Intent) {
		handler.removeCallbacks(runnable)
		handler.postDelayed(runnable, 100)
	}
}
