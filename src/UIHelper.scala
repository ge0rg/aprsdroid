package org.aprsdroid.app
// this class is a hack containing all the common UI code for different Activity subclasses

import _root_.android.app.Activity
import _root_.android.app.AlertDialog
import _root_.android.content.{BroadcastReceiver, Context, DialogInterface, Intent, IntentFilter}
import _root_.android.net.Uri
import _root_.android.util.Log
import _root_.android.view.{LayoutInflater, Menu, MenuItem, View}
import _root_.android.widget.{EditText, Toast}

class UIHelper(ctx : Activity, menu_id : Int, prefs : PrefsWrapper)
	extends DialogInterface.OnClickListener 
	   with DialogInterface.OnCancelListener {

	var openedPrefs = false

	def onStartLoading() {
		ctx.asInstanceOf[LoadingIndicator].onStartLoading()
	}

	def trackOnMap(call : String) {
		val text = ctx.getString(R.string.map_track_call, call)
		Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()
		ctx.startActivity(new Intent(ctx, classOf[MapAct]).putExtra("call", call))
	}

	def openPrefs(toastId : Int) {
		if (openedPrefs) {
			// only open prefs once, exit app afterwards
			ctx.finish()
		} else {
			ctx.startActivity(new Intent(ctx, classOf[PrefsAct]));
			Toast.makeText(ctx, toastId, Toast.LENGTH_SHORT).show()
			openedPrefs = true
		}
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

	def firstRunDialog() = {
			val inflater = ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE)
					.asInstanceOf[LayoutInflater]
			val fr_view = inflater.inflate(R.layout.firstrunview, null, false)
			val fr_call = fr_view.findViewById(R.id.callsign).asInstanceOf[EditText]
			val fr_pass = fr_view.findViewById(R.id.passcode).asInstanceOf[EditText]
			new AlertDialog.Builder(ctx).setTitle(ctx.getString(R.string.fr_title))
				.setView(fr_view)
				.setIcon(android.R.drawable.ic_dialog_info)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					override def onClick(d : DialogInterface, which : Int) {
						which match {
							case DialogInterface.BUTTON_POSITIVE =>
							prefs.prefs.edit().putString("callsign", fr_call.getText().toString())
									.putString("passcode", fr_pass.getText().toString())
									.putBoolean("firstrun", false).commit();
							checkConfig()
							case _ =>
							ctx.finish()
						}
					}})
				.setNeutralButton(R.string.p_passreq, new UrlOpener(ctx, ctx.getString(R.string.passcode_url)))
				.setNegativeButton(android.R.string.cancel, this)
				.setOnCancelListener(this)
				.create.show
	}
	// DialogInterface.OnClickListener
	override def onClick(d : DialogInterface, which : Int) {
		ctx.finish()
	}
	// DialogInterface.OnCancelListener
	override def onCancel(d : DialogInterface) {
		ctx.finish()
	}

	def checkConfig() : Boolean = {
		val callsign = prefs.getCallsign()
		val passcode = prefs.getPasscode()
		if (callsign == "" || prefs.getBoolean("firstrun", true)) {
			firstRunDialog()
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

	def ageDialog() {
		val minutes = ctx.getResources().getStringArray(R.array.age_minutes)
		val selected = minutes.indexOf(prefs.getString("show_age", "30"))

		new AlertDialog.Builder(ctx).setTitle(ctx.getString(R.string.age))
			.setSingleChoiceItems(R.array.ages, selected, new DialogInterface.OnClickListener() {
					override def onClick(d : DialogInterface, which : Int) {
						Log.d("onClick", "clicked on: " + d + " " + which)
						val min = ctx.getResources().getStringArray(R.array.age_minutes)(which)
						prefs.prefs.edit().putString("show_age", min).commit()
						ctx.sendBroadcast(new Intent(AprsService.UPDATE))
						onStartLoading()
						d.dismiss()
					}})
			//.setPositiveButton(android.R.string.ok, null)
			//.setNegativeButton(android.R.string.cancel, null)
			.create.show
	}

	def onPrepareOptionsMenu(menu : Menu) : Boolean = {
		val mi = menu.findItem(R.id.startstopbtn)
		mi.setTitle(if (AprsService.running) R.string.stoplog else R.string.startlog)
		mi.setIcon(if (AprsService.running) android.R.drawable.ic_menu_close_clear_cancel  else android.R.drawable.ic_menu_compass)
		// disable the "own" menu
		Array(R.id.hub, R.id.map, R.id.log).map((id) => {
			menu.findItem(id).setVisible(id != menu_id)
		})
		menu.findItem(R.id.overlays).setVisible(R.id.map == menu_id)
		menu.findItem(R.id.objects).setChecked(prefs.getShowObjects())
		menu.findItem(R.id.satellite).setChecked(prefs.getShowSatellite())
		true
	}

	def optionsItemAction(mi : MenuItem) : Boolean = {
		mi.getItemId match {
		case R.id.preferences =>
			ctx.startActivity(new Intent(ctx, classOf[PrefsAct]));
			true
		case R.id.clear =>
			onStartLoading()
			new StorageCleaner(StorageDatabase.open(ctx)).execute()
			true
		case R.id.about =>
			aboutDialog()
			true
		case R.id.age =>
			ageDialog()
			true
		// switch between activities
		case R.id.hub =>
			ctx.startActivity(new Intent(ctx, classOf[HubActivity]));
			true
		case R.id.map =>
			ctx.startActivity(new Intent(ctx, classOf[MapAct]));
			true
		case R.id.log =>
			ctx.startActivity(new Intent(ctx, classOf[APRSdroid]));
			true
		// toggle service
		case R.id.startstopbtn =>
			val is_running = AprsService.running
			if (!is_running) {
				passcodeWarning(prefs.getCallsign(), prefs.getPasscode())
				ctx.startService(AprsService.intent(ctx, AprsService.SERVICE))
			} else {
				ctx.stopService(AprsService.intent(ctx, AprsService.SERVICE))
			}
			true
		case R.id.singlebtn =>
			passcodeWarning(prefs.getCallsign(), prefs.getPasscode())
			ctx.startService(AprsService.intent(ctx, AprsService.SERVICE_ONCE))
			true
		// quit the app
		//case R.id.quit =>
		//	// XXX deprecated!
		//	ctx.stopService(AprsService.intent(ctx, AprsService.SERVICE))
		//	ctx.finish();
		//	true
		case _ => false
		}
	}

	class StorageCleaner(storage : StorageDatabase) extends MyAsyncTask[Unit, Unit] {
		override def doInBackground1(params : Array[String]) {
			Log.d("StorageCleaner", "trimming...")
			storage.trimPosts(System.currentTimeMillis)
		}
		override def onPostExecute(x : Unit) {
			Log.d("StorageCleaner", "broadcasting...")
			ctx.sendBroadcast(new Intent(AprsService.UPDATE))
		}
	}
}

class UrlOpener(ctx : Context, url : String) extends DialogInterface.OnClickListener {
	override def onClick(d : DialogInterface, which : Int) {
		ctx.startActivity(new Intent(Intent.ACTION_VIEW,
			Uri.parse(url)))
	}
}

