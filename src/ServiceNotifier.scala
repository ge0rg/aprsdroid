package org.aprsdroid.app

import _root_.android.app.{Notification, NotificationManager, PendingIntent, Service}
import _root_.android.content.{Context, Intent}
import _root_.android.net.Uri
import _root_.android.os.Build
import _root_.android.graphics.Color


object ServiceNotifier {
	val instance = if (Build.VERSION.SDK.toInt < 5) new DonutNotifier() else new EclairNotifier()
}

abstract class ServiceNotifier {
	val SERVICE_NOTIFICATION : Int = 1
	var CALL_NOTIFICATION = SERVICE_NOTIFICATION + 1
	val callIdMap = new scala.collection.mutable.HashMap[String, Int]()

	def newNotification(ctx : Service, status : String) : Notification = {
		val n = new Notification()
		n.icon = R.drawable.ic_status
		n.when = System.currentTimeMillis
		n.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR
		val i = new Intent(ctx, classOf[APRSdroid])
		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		n.contentIntent = PendingIntent.getActivity(ctx, 0, i, 0)
		val appname = ctx.getResources().getString(R.string.app_name)
		n.setLatestEventInfo(ctx, appname, status, n.contentIntent)
		n
	}

	def getCallNumber(call : String) : Int = {
		if (callIdMap.contains(call)) {
			callIdMap(call)
		} else {
			val id = CALL_NOTIFICATION
			CALL_NOTIFICATION += 1
			callIdMap(call) = id
			id
		}
	}

	def newMessageNotification(ctx : Service, call : String, message : String) : Notification = {
		val n = new Notification()
		n.icon = R.drawable.icon
		n.when = System.currentTimeMillis
		n.flags = Notification.FLAG_AUTO_CANCEL
		val i = new Intent(ctx, classOf[MessageActivity])
		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		i.setData(Uri.parse(call))
		n.contentIntent = PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT)
		n.setLatestEventInfo(ctx, call, message, n.contentIntent)
		n
	}

	def getNotificationMgr(ctx : Context) = ctx.getSystemService(Context.NOTIFICATION_SERVICE).asInstanceOf[NotificationManager]

	def start(ctx : Service, status : String)
	def stop(ctx : Service)

	def setupNotification(n : Notification, prefs : PrefsWrapper, prefix : String) {
		// set notification LED
		if (prefs.getBoolean(prefix + "notify_led", true)) {
			n.ledARGB = Color.YELLOW
			n.ledOnMS = 300
			n.ledOffMS = 1000
			n.flags |= Notification.FLAG_SHOW_LIGHTS
		}
		if (prefs.getBoolean(prefix + "notify_vibr", true)) {
			n.vibrate = Array[Long](0, 200, 200)
		}
		n.sound = Uri.parse(prefs.getString(prefix + "notify_ringtone", null))
	}

	def notifyMessage(ctx : Service, prefs : PrefsWrapper,
			call : String, message : String) {
		val n = newMessageNotification(ctx, call, message)
		// set notification LED
		setupNotification(n, prefs, "")
		getNotificationMgr(ctx).notify(getCallNumber(call),
			n)
	}

	def cancelMessage(ctx : Context, call : String) {
		getNotificationMgr(ctx).cancel(getCallNumber(call))
	}

	def notifyPosition(ctx : Service, prefs : PrefsWrapper,
			status : String) {
		val n = newNotification(ctx, status)
		setupNotification(n, prefs, "pos_")
		getNotificationMgr(ctx).notify(SERVICE_NOTIFICATION, n)
	}
}

class DonutNotifier extends ServiceNotifier {
	def start(ctx : Service, status : String) = {
		ctx.setForeground(true)
		getNotificationMgr(ctx).notify(SERVICE_NOTIFICATION, newNotification(ctx, status))
	}

	def stop(ctx : Service) = {
		ctx.setForeground(false)
		getNotificationMgr(ctx).cancel(SERVICE_NOTIFICATION)
	}
}

class EclairNotifier extends ServiceNotifier {
	def start(ctx : Service, status : String) = {
		ctx.startForeground(SERVICE_NOTIFICATION, newNotification(ctx, status))
	}

	def stop(ctx : Service) = {
		ctx.stopForeground(true)
	}
}

