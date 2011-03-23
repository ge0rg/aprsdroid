package org.aprsdroid.app
// this class is a hack containing all the common UI code for different Activity subclasses

import _root_.android.app.Activity
import _root_.android.app.AlertDialog
import _root_.android.content.{BroadcastReceiver, Context, DialogInterface, Intent, IntentFilter}
import _root_.android.net.Uri
import _root_.android.view.{LayoutInflater, Menu, MenuItem, View}
import _root_.android.widget.Toast

class UIHelper(ctx : Activity, prefs : PrefsWrapper) {

	def openPrefs(toastId : Int) {
		ctx.startActivity(new Intent(ctx, classOf[PrefsAct]));
		Toast.makeText(ctx, toastId, Toast.LENGTH_SHORT).show()
	}

	def passcodeWarning(call : String, pass : String) {
		import Backend._
		if ((defaultBackendInfo(prefs).need_passcode == PASSCODE_OPTIONAL) &&
				!AprsPacket.passcodeAllowed(call, pass, false))
			Toast.makeText(ctx, R.string.anon_warning, Toast.LENGTH_LONG).show()
	}


	def passcodeConfigRequired(call : String, pass : String) : Boolean = {
		import Backend._
		// a valid passcode must be entered for "required",
		// "" and "-1" are accepted as well for "optional"
		defaultBackendInfo(prefs).need_passcode match {
		case PASSCODE_NONE => false
		case PASSCODE_OPTIONAL =>
			!AprsPacket.passcodeAllowed(call, pass, true)
		case PASSCODE_REQUIRED =>
			!AprsPacket.passcodeAllowed(call, pass, false)
		}
	}

	def checkConfig() : Boolean = {
		val callsign = prefs.getCallsign()
		val passcode = prefs.getPasscode()
		if (callsign == "") {
			openPrefs(R.string.firstrun)
			return false
		}
		if (passcodeConfigRequired(callsign, passcode)) {
			openPrefs(R.string.wrongpasscode)
			return false
		} else passcodeWarning(callsign, passcode)

		if (prefs.getStringInt("interval", 10) < 1) {
			openPrefs(R.string.mininterval)
			return false
		}
		true
	}


	def aboutDialog() {
		val pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0)
		val title = ctx.getString(R.string.ad_title, pi.versionName);
		val inflater = ctx.getLayoutInflater()
		val aboutview = inflater.inflate(R.layout.aboutview, null)
		new AlertDialog.Builder(ctx).setTitle(title)
			.setView(aboutview)
			.setIcon(android.R.drawable.ic_dialog_info)
			.setPositiveButton(android.R.string.ok, null)
			.setNeutralButton(R.string.ad_homepage, new UrlOpener(ctx, "http://aprsdroid.org/"))
			.create.show
	}

	def showFirstRunDialog() {
	}


	def optionsItemAction(mi : MenuItem) : Boolean = {
		mi.getItemId match {
		case R.id.preferences =>
			ctx.startActivity(new Intent(ctx, classOf[PrefsAct]));
			true
		case R.id.clear =>
			//storage.trimPosts(System.currentTimeMillis)
			//postcursor.requery()
			true
		case R.id.about =>
			aboutDialog()
			true
		case R.id.map =>
			ctx.startActivity(new Intent(ctx, classOf[MapAct]));
			true
		case R.id.startstopbtn =>
			val is_running = AprsService.running
			if (!is_running) {
				ctx.startService(AprsService.intent(ctx, AprsService.SERVICE))
			} else {
				ctx.stopService(AprsService.intent(ctx, AprsService.SERVICE))
			}
			true
		case R.id.quit =>
			// XXX deprecated!
			ctx.stopService(AprsService.intent(ctx, AprsService.SERVICE))
			ctx.finish();
			true
		case _ => false
		}
	}

}

class UrlOpener(ctx : Context, url : String) extends DialogInterface.OnClickListener {
	override def onClick(d : DialogInterface, which : Int) {
		ctx.startActivity(new Intent(Intent.ACTION_VIEW,
			Uri.parse(url)))
	}
}

