package org.aprsdroid.app

import android.content.{BroadcastReceiver, Context, Intent}
import android.os.Build

class SystemEventReceiver extends BroadcastReceiver {
	val TAG = "APRSdroid.SystemEventReceiver"

	override def onReceive(ctx : Context, i : Intent) {
		android.util.Log.d(TAG, "onReceive: " + i)
		val prefs = new PrefsWrapper(ctx)
		if (prefs.getBoolean("service_running", false)) {
                        val i = AprsService.intent(ctx, AprsService.SERVICE)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                                ctx.startForegroundService(i)
                        else
                                ctx.startService(i)
                }
	}
}
