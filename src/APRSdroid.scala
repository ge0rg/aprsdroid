package org.aprsdroid.app

import _root_.android.app.Activity
import _root_.android.content.Intent
import _root_.android.os.Bundle
import _root_.android.preference.PreferenceManager

class APRSdroid extends Activity {
	def replaceAct(act : Class[_]) {
		val i = new Intent(this, act)
		i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
		startActivity(i)
		finish()
	}

	override def onCreate(savedInstanceState : Bundle) {
		super.onCreate(savedInstanceState)
		val prefs = PreferenceManager.getDefaultSharedPreferences(this)

		// if this is a USB device, auto-launch the service
		if (UsbTnc.checkDeviceHandle(prefs, getIntent.getParcelableExtra("device")) && prefs.getBoolean("service_running", false))
			startService(AprsService.intent(this, AprsService.SERVICE))

		val mapmode = MapModes.defaultMapMode(this, new PrefsWrapper(this))
		prefs.getString("activity", "log") match {
		case "hub" => replaceAct(classOf[HubActivity])
		case "map" => replaceAct(mapmode.viewClass)
		case _ => replaceAct(classOf[LogActivity])
		}
	}
}
