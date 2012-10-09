package org.aprsdroid.app

import _root_.android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import _root_.android.graphics.PorterDuff
import _root_.android.view.View.OnClickListener
import _root_.android.view.{ContextMenu, Menu, MenuItem, View, Window}
import _root_.android.widget.Button

class MainListActivity(actname : String, menuid : Int) extends LoadingListActivity
		with OnClickListener {
	menu_id = menuid

	lazy val singleBtn = findViewById(R.id.singlebtn).asInstanceOf[Button]
	lazy val startstopBtn = findViewById(R.id.startstopbtn).asInstanceOf[Button]

	lazy val miclReceiver = new BroadcastReceiver() {
		override def onReceive(ctx : Context, i : Intent) {
			setProgress(i.getIntExtra("level", 100)*99)
		}
	}


	def onContentViewLoaded() {
		singleBtn.setOnClickListener(this);
		startstopBtn.setOnClickListener(this);
		registerForContextMenu(getListView())
	}

	override def onResume() {
		super.onResume()
		if (!checkConfig())
			return
		setupButtons(AprsService.running)
		makeLaunchActivity(actname)
		setKeepScreenOn()
		setVolumeControls()

		registerReceiver(miclReceiver, new IntentFilter(AprsService.MICLEVEL))
	}
	override def onPause() {
		super.onPause()
		unregisterReceiver(miclReceiver)
	}

	override def onCreateOptionsMenu(menu : Menu) : Boolean = {
		getMenuInflater().inflate(R.menu.options, menu);
		true
	}

	def setupButtons(running : Boolean) {
		//singleBtn.setEnabled(!running)
		if (running) {
			startstopBtn.getBackground().setColorFilter(0xffffc0c0, PorterDuff.Mode.MULTIPLY)
			startstopBtn.setText(R.string.stoplog)
		} else {
			startstopBtn.getBackground().setColorFilter(0xffc0ffc0, PorterDuff.Mode.MULTIPLY)
			startstopBtn.setText(R.string.startlog)
		}
	}

	override def onClick(view : View) {
		view.getId match {
		case R.id.singlebtn =>
			passcodeWarning(prefs.getCallsign(), prefs.getPasscode())
			startService(AprsService.intent(this, AprsService.SERVICE_ONCE))
			setupButtons(true)
		case R.id.startstopbtn =>
			val is_running = AprsService.running
			if (!is_running) {
				startService(AprsService.intent(this, AprsService.SERVICE))
			} else {
				stopService(AprsService.intent(this, AprsService.SERVICE))
			}
			setupButtons(!is_running)
		}
	}

	override def onStopLoading() {
		super.onStopLoading()
		setupButtons(AprsService.running)
	}

}
