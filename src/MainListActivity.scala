package org.aprsdroid.app

import _root_.android.graphics.PorterDuff
import _root_.android.view.View.OnClickListener
import _root_.android.view.{Menu, MenuItem, View, Window}
import _root_.android.widget.Button

class MainListActivity(menuid : Int) extends LoadingListActivity with OnClickListener {
	lazy val prefs = new PrefsWrapper(this)
	lazy val uihelper = new UIHelper(this, menuid, prefs)

	lazy val singleBtn = findViewById(R.id.singlebtn).asInstanceOf[Button]
	lazy val startstopBtn = findViewById(R.id.startstopbtn).asInstanceOf[Button]

	def onContentViewLoaded() {
		singleBtn.setOnClickListener(this);
		startstopBtn.setOnClickListener(this);

	}

	override def onResume() {
		super.onResume()
		if (!uihelper.checkConfig())
			return
		setupButtons(AprsService.running)
	}

	override def onCreateOptionsMenu(menu : Menu) : Boolean = {
		getMenuInflater().inflate(R.menu.options, menu);
		true
	}
	override def onPrepareOptionsMenu(menu : Menu) = uihelper.onPrepareOptionsMenu(menu)

	override def onOptionsItemSelected(mi : MenuItem) : Boolean = {
		uihelper.optionsItemAction(mi)
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
			uihelper.passcodeWarning(prefs.getCallsign(), prefs.getPasscode())
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
