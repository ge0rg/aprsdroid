package org.aprsdroid.app

import android.content.{BroadcastReceiver, Context, Intent}

class SystemEventReceiver extends BroadcastReceiver {
	val TAG = "APRSdroid.SystemEventReceiver"

	override def onReceive(ctx : Context, i : Intent) {
		android.util.Log.d(TAG, "onReceive: " + i)
		val prefs = new PrefsWrapper(ctx)
		if (prefs.getBoolean("service_running", false))
			ctx.startService(AprsService.intent(ctx, AprsService.SERVICE))
	}
}
