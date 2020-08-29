package org.aprsdroid.app
// this class is a hack containing all the common UI code for different Activity subclasses

import _root_.android.app.{Activity, ListActivity}
import _root_.android.app.AlertDialog
import _root_.android.content.{BroadcastReceiver, Context, DialogInterface, Intent, IntentFilter}
import _root_.android.content.res.Configuration
import _root_.android.net.Uri
import _root_.android.os.{Build, Environment}
import _root_.android.util.Log
import _root_.android.view.{ContextMenu, LayoutInflater, Menu, MenuItem, View, WindowManager}
import _root_.android.widget.AdapterView.AdapterContextMenuInfo
import _root_.android.widget.{EditText, Toast}
import java.io.{File, PrintWriter}
import java.text.SimpleDateFormat
import java.util.Date

import android.content.pm.PackageManager
import android.provider.Settings

trait UIHelper extends Activity
		with LoadingIndicator
		with PermissionHelper
		with DialogInterface.OnClickListener 
		with DialogInterface.OnCancelListener {

	var menu_id : Int = -1
	lazy val prefs = new PrefsWrapper(this)
	var openedPrefs = false

	// thx to http://robots.thoughtbot.com/post/5836463058/scala-a-better-java-for-android
	def findView[WidgetType] (id : Int) : WidgetType = {
		findViewById(id).asInstanceOf[WidgetType]
	}

	def openDetails(call : String) {
		startActivity(new Intent(this, classOf[StationActivity]).setData(Uri.parse(call)))
	}

	def openMessaging(call : String) {
		startActivity(new Intent(this, classOf[MessageActivity]).setData(Uri.parse(call)))
	}

	def clearMessages(call : String) {
		new MessageCleaner(StorageDatabase.open(this), call).execute()
	}

	def openMessageSend(call : String, message : String) {
		startActivity(new Intent(this, classOf[MessageActivity]).setData(Uri.parse(call)).putExtra("message", message))
	}

	def trackOnMap(call : String) {
		val text = getString(R.string.map_track_call, call)
		Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
		MapModes.startMap(this, prefs, call)
	}

	def openPrefs(toastId : Int, act : Class[_]) {
		if (openedPrefs) {
			// only open prefs once, exit app afterwards
			finish()
		} else {
			startActivity(new Intent(this, act));
			Toast.makeText(this, toastId, Toast.LENGTH_SHORT).show()
			openedPrefs = true
		}
	}
	def currentListOfPermissions() : Array[String] = {
		val bi_perms = AprsBackend.defaultBackendPermissions(prefs)
		val ls_perms = LocationSource.getPermissions(prefs)
		(bi_perms ++ ls_perms).toArray
	}

	val START_SERVICE = 1001
	val START_SERVICE_ONCE = 1002

	override def getActionName(action : Int): Int = {
		action match {
		case START_SERVICE => R.string.startlog
		case START_SERVICE_ONCE => R.string.singlelog
		}
	}
	override def onAllPermissionsGranted(action : Int): Unit = {
		action match {
		case START_SERVICE => startService(AprsService.intent(this, AprsService.SERVICE))
		case START_SERVICE_ONCE => startService(AprsService.intent(this, AprsService.SERVICE_ONCE))
		}
	}
	def startAprsService(action : Int): Unit = {
		checkPermissions(currentListOfPermissions(), action)
	}

	// manual stop: remember shutdown for next reboot
	def stopAprsService() {
		// explicitly disabled, remember this
		prefs.setBoolean("service_running", false)
		stopService(AprsService.intent(this, AprsService.SERVICE))
	}

	def passcodeConfigRequired(call : String, pass : String) : Boolean = {
		import AprsBackend._
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

	def saveFirstRun(call : String, passcode : String) {
		val pe = prefs.prefs.edit()
		call.split("-") match {
		case Array(callsign) =>
			pe.putString("callsign", callsign)
		case Array(callsign, ssid) =>
			pe.putString("callsign", callsign)
			pe.putString("ssid", ssid)
		case _ =>
			Log.d("saveFirstRun", "could not split callsign")
			finish()
			return
		}
		if (passcode != "")
			pe.putString("passcode", passcode)
		pe.putBoolean("firstrun", false)
		pe.commit()
	}

	def firstRunDialog() = {
			val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE)
					.asInstanceOf[LayoutInflater]
			val fr_view = inflater.inflate(R.layout.firstrunview, null, false)
			val fr_call = fr_view.findViewById(R.id.callsign).asInstanceOf[EditText]
			val fr_pass = fr_view.findViewById(R.id.passcode).asInstanceOf[EditText]
			new AlertDialog.Builder(this).setTitle(getString(R.string.fr_title))
				.setView(fr_view)
				.setIcon(android.R.drawable.ic_dialog_info)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					override def onClick(d : DialogInterface, which : Int) {
						which match {
							case DialogInterface.BUTTON_POSITIVE =>
							saveFirstRun(fr_call.getText().toString(),
								fr_pass.getText().toString())
							checkConfig()
							case _ =>
							finish()
						}
					}})
				.setNeutralButton(R.string.p_passreq, new UrlOpener(this, getString(R.string.passcode_url)))
				.setOnCancelListener(this)
				.create.show
	}
	// DialogInterface.OnClickListener
	override def onClick(d : DialogInterface, which : Int) {
		finish()
	}
	// DialogInterface.OnCancelListener
	override def onCancel(d : DialogInterface) {
		finish()
	}

	def setTitleStatus() {
		if (AprsService.link_error != 0) {
			setTitle(getString(R.string.status_linkoff, getString(AprsService.link_error)))
		} else {
			val title = getPackageManager().getActivityInfo(getComponentName(), 0).labelRes
			setTitle(title)
		}
	}

	def setLongTitle(title_id : Int, targetcall : String) {
		// use two-line display on holo in portrait mode
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
				new HoneycombTitleSetter(getString(title_id), targetcall)
			else
				new HoneycombTitleSetter(getString(title_id) + ": " + targetcall, null)
		} else // pre-holo setTitle
			setTitle(getString(title_id) + ": " + targetcall)
	}
	class HoneycombTitleSetter(t : String, st : String) {
		UIHelper.this.setTitle(t)
		UIHelper.this.getActionBar().setSubtitle(st)
	}

	// store the activity name for next APRSdroid launch
	def makeLaunchActivity(actname : String) {
		prefs.prefs.edit().putString("activity", actname).commit()
	}

	// keep screen on all the time if requested
	def setKeepScreenOn() {
		if (prefs.getBoolean("keepscreen", false)) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		} else {
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		}
	}

	// for AFSK, set the right volume controls
	def setVolumeControls() {
		if (prefs.getString("backend", AprsBackend.DEFAULT_CONNTYPE) == "afsk") {
			setVolumeControlStream(prefs.getAfskOutput())
		}
	}

	def checkConfig() : Boolean = {
		val callsign = prefs.getCallsign()
		val passcode = prefs.getPasscode()
		if (callsign == "" || prefs.getBoolean("firstrun", true)) {
			firstRunDialog()
			return false
		}
		if (passcodeConfigRequired(callsign, passcode)) {
			openPrefs(R.string.wrongpasscode, classOf[BackendPrefs])
			return false
		}

		if (prefs.getStringInt("interval", 10) < 1) {
			openPrefs(R.string.mininterval, classOf[PrefsAct])
			return false
		}
		if (prefs.getString("proto", null) == null) {
			// upgrade to 1.4+, need to set "proto" and "link"/"aprsis"
			val proto_link_aprsis = AprsBackend.backend_upgrade(prefs.getString("backend", "tcp")).split("-")
			prefs.prefs.edit()
				.putString("proto", proto_link_aprsis(0))
				.putString("link", proto_link_aprsis(1))
				.putString("aprsis", proto_link_aprsis(2))
				.commit()
		}
		true
	}

	def aboutDialog() {
		val pi = getPackageManager().getPackageInfo(this.getPackageName(), 0)
		val title = getString(R.string.ad_title, pi.versionName);
		val inflater = getLayoutInflater()
		val aboutview = inflater.inflate(R.layout.aboutview, null)
		new AlertDialog.Builder(this).setTitle(title)
			.setView(aboutview)
			.setIcon(android.R.drawable.ic_dialog_info)
			.setPositiveButton(android.R.string.ok, null)
			.setNeutralButton(R.string.ad_homepage, new UrlOpener(this, "https://aprsdroid.org/"))
			.create.show
	}

	def ageDialog() {
		val minutes = getResources().getStringArray(R.array.age_minutes)
		val selected = minutes.indexOf(prefs.getString("show_age", "30"))

		new AlertDialog.Builder(this).setTitle(getString(R.string.age))
			.setSingleChoiceItems(R.array.ages, selected, new DialogInterface.OnClickListener() {
					override def onClick(d : DialogInterface, which : Int) {
						Log.d("onClick", "clicked on: " + d + " " + which)
						val min = getResources().getStringArray(R.array.age_minutes)(which)
						prefs.prefs.edit().putString("show_age", min).commit()
						sendBroadcast(new Intent(AprsService.UPDATE))
						onStartLoading()
						d.dismiss()
					}})
			//.setPositiveButton(android.R.string.ok, null)
			//.setNegativeButton(android.R.string.cancel, null)
			.create.show
	}

	def sendMessageBroadcast(dest : String, body : String) {
		sendBroadcast(new Intent(AprsService.MESSAGETX)
			.putExtra(AprsService.SOURCE, prefs.getCallSsid())
			.putExtra(AprsService.DEST, dest)
			.putExtra(AprsService.BODY, body)
			)
	}

	abstract override def onCreateOptionsMenu(menu : Menu) : Boolean = {
		getMenuInflater().inflate(R.menu.options_activities, menu);
		getMenuInflater().inflate(R.menu.options_map, menu);
		getMenuInflater().inflate(R.menu.options, menu);
		// disable the "own" menu
		Array(R.id.hub, R.id.map, R.id.log, R.id.conversations).map((id) => {
			menu.findItem(id).setVisible(id != menu_id)
		})
		menu.findItem(R.id.age).setVisible(R.id.map == menu_id || R.id.hub == menu_id)
		menu.findItem(R.id.overlays).setVisible(R.id.map == menu_id)
		true
	}

	abstract override def onPrepareOptionsMenu(menu : Menu) : Boolean = {
		val mi = menu.findItem(R.id.startstopbtn)
		mi.setTitle(if (AprsService.running) R.string.stoplog else R.string.startlog)
		mi.setIcon(if (AprsService.running) android.R.drawable.ic_menu_close_clear_cancel  else android.R.drawable.ic_menu_compass)
		menu.findItem(R.id.objects).setChecked(prefs.getShowObjects())
		menu.findItem(R.id.satellite).setChecked(prefs.getShowSatellite())
		true
	}

	abstract override def onOptionsItemSelected(mi : MenuItem) : Boolean = {
		mi.getItemId match {
		case R.id.preferences =>
			startActivity(new Intent(this, classOf[PrefsAct]));
			true
		case R.id.export =>
			onStartLoading()
			new LogExporter(StorageDatabase.open(this), null).execute()
			true
		case R.id.clear =>
			onStartLoading()
			new StorageCleaner(StorageDatabase.open(this)).execute()
			true
		case R.id.about =>
			aboutDialog()
			true
		case R.id.age =>
			ageDialog()
			true
		// switch between activities
		case R.id.hub =>
			startActivity(new Intent(this, classOf[HubActivity]).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
			true
		case R.id.map =>
			MapModes.startMap(this, prefs, "")
			true
		case R.id.log =>
			startActivity(new Intent(this, classOf[LogActivity]).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
			true
		case R.id.conversations =>
			startActivity(new Intent(this, classOf[ConversationsActivity]).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
			true
		// toggle service
		case R.id.startstopbtn =>
			val is_running = AprsService.running
			if (!is_running) {
				startAprsService(START_SERVICE)
			} else {
				stopAprsService()
			}
			true
		case R.id.singlebtn =>
			startAprsService(START_SERVICE_ONCE)
			true
		// quit the app
		//case R.id.quit =>
		//	// XXX deprecated!
		//	stopService(AprsService.intent(this, AprsService.SERVICE))
		//	finish();
		//	true
		case android.R.id.home =>
			if (isTaskRoot()) {
				startActivity(new Intent(this, classOf[HubActivity]).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
				finish();
				true
			} else super.onOptionsItemSelected(mi)
		case _ => false
		}
	}
	
	abstract override def onCreateContextMenu(menu : ContextMenu, v : View,
			menuInfo : ContextMenu.ContextMenuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo)
		val call = menuInfoCall(menuInfo)
		if (call == null)
			return
		getMenuInflater().inflate(R.menu.context_call, menu)
		menu.setHeaderTitle(call)
	}
	def menuInfoCall(menuInfo : ContextMenu.ContextMenuInfo) : String = {
		val i = menuInfo.asInstanceOf[AdapterContextMenuInfo]
		// a listview with a database backend gives out a cursor :D
		val c = asInstanceOf[ListActivity].getListView()
			.getItemAtPosition(i.position).asInstanceOf[android.database.Cursor]
		StorageDatabase.cursor2call(c)
	}

	def getStaPosition(db : StorageDatabase, targetcall : String) = {
		val cursor = db.getStaPosition(targetcall)
		if (cursor.getCount() > 0) {
			cursor.moveToFirst()
			val lat = cursor.getInt(StorageDatabase.Station.COLUMN_LAT)
			val lon = cursor.getInt(StorageDatabase.Station.COLUMN_LON)
			cursor.close()
			Log.d("GetStaPos", "Found " + targetcall +" " + lat + " " + lon)
			(true, lat, lon)
		} else {
			Toast.makeText(this, getString(R.string.map_track_unknown, targetcall), Toast.LENGTH_SHORT).show()
			cursor.close()
			Log.d("GetStaPos", "Missed " + targetcall)
			(false, 0, 0)
		}
	}

	def callsignAction(id : Int, targetcall : String) : Boolean = {
		val basecall = targetcall.split("[- ]+")(0)
		id match {
		case R.id.details =>
			openDetails(targetcall)
			true
		case R.id.message =>
			openMessaging(targetcall)
			true
		case R.id.messagesclear =>
			clearMessages(targetcall)
			true
		case R.id.map =>
			trackOnMap(targetcall)
			true
		case R.id.extmap =>
			val (found, lat, lon) = getStaPosition(StorageDatabase.open(this), targetcall)
			if (found) {
				val url = "geo:%1.6f,%1.6f?q=%1.6f,%1.6f(%s)".formatLocal(null,
					lat/1000000.0, lon/1000000.0, lat/1000000.0, lon/1000000.0, targetcall)
				startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW,
					Uri.parse(url)), targetcall))
			}
			true
		case R.id.aprsfi =>
			val url = "https://aprs.fi/info/a/%s?utm_source=aprsdroid&utm_medium=inapp&utm_campaign=aprsfi".format(targetcall)
			startActivity(new Intent(Intent.ACTION_VIEW,
				Uri.parse(url)))
			true
		case R.id.qrzcom =>
			val url = "https://qrz.com/db/%s".format(basecall)
			startActivity(new Intent(Intent.ACTION_VIEW,
				Uri.parse(url)))
			true
		case R.id.sta_export =>
			new LogExporter(StorageDatabase.open(this), basecall).execute()
			true
		case _ =>
			false
		}
	}
	abstract override def onContextItemSelected(item : MenuItem) : Boolean = {
		val targetcall = menuInfoCall(item.getMenuInfo)
		callsignAction(item.getItemId(), targetcall)
	}


	class StorageCleaner(storage : StorageDatabase) extends MyAsyncTask[Unit, Unit] {
		override def doInBackground1(params : Array[String]) {
			Log.d("StorageCleaner", "trimming...")
			storage.trimPosts(Long.MaxValue)
		}
		override def onPostExecute(x : Unit) {
			Log.d("StorageCleaner", "broadcasting...")
			sendBroadcast(new Intent(AprsService.UPDATE))
		}
	}
	class MessageCleaner(storage : StorageDatabase, call : String) extends MyAsyncTask[Unit, Unit] {
		override def doInBackground1(params : Array[String]) {
			Log.d("MessageCleaner", "deleting...")
			storage.deleteMessages(call)
		}
		override def onPostExecute(x : Unit) {
			Log.d("MessageCleaner", "broadcasting...")
			sendBroadcast(AprsService.MSG_PRIV_INTENT)
		}
	}
	class LogExporter(storage : StorageDatabase, call : String) extends MyAsyncTask[Unit, String] {
		val filename = "aprsdroid-%s.log".format(new SimpleDateFormat("yyyyMMdd-HHmm").format(new Date()))
		val file = new File(Environment.getExternalStorageDirectory(), filename)

		override def doInBackground1(params : Array[String]) : String = {
			import StorageDatabase.Post._
			Log.d("LogExporter", "saving " + filename + " for callsign " + call)
			val c = storage.getExportPosts(call)
			if (c.getCount() == 0) {
				return getString(R.string.export_empty)
			}
			try {
				val fo = new PrintWriter(file)
				while (c.moveToNext()) {
					val ts = c.getString(COLUMN_TSS)
					val tpe = c.getInt(COLUMN_TYPE)
					val packet = c.getString(COLUMN_MESSAGE)
					fo.print(ts)
					fo.print("\t")
					fo.print(if (tpe == TYPE_INCMG) "RX" else "TX")
					fo.print("\t")
					fo.println(packet)
				}
				fo.close()
				return null
			} catch {
			case e : Exception => return e.getMessage()
			} finally {
				c.close()
			}
		}
		override def onPostExecute(error : String) {
			Log.d("LogExporter", "saving " + filename + " done: " + error)
			onStopLoading()
			if (error != null)
				Toast.makeText(UIHelper.this, error, Toast.LENGTH_SHORT).show()
			else startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
					.setType("text/plain")
					.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))
					.putExtra(Intent.EXTRA_SUBJECT, filename),
				file.toString()))
		}
	}
}

class UrlOpener(ctx : Context, url : String) extends DialogInterface.OnClickListener {
	override def onClick(d : DialogInterface, which : Int) {
		ctx.startActivity(new Intent(Intent.ACTION_VIEW,
			Uri.parse(url)))
	}
}

