package org.aprsdroid.app

import _root_.android.app.{Notification, NotificationChannel, NotificationManager, PendingIntent, Service}
import _root_.android.content.{Context, Intent}
import _root_.android.net.Uri
import _root_.android.os.Build
import _root_.android.os.Vibrator
import _root_.android.graphics.Color


object ServiceNotifier {
	val instance = new ServiceNotifier()
}

class ServiceNotifier {
	val SERVICE_NOTIFICATION : Int = 1
	var CALL_NOTIFICATION = SERVICE_NOTIFICATION + 1
	val callIdMap = new scala.collection.mutable.HashMap[String, Int]()

	def setupChannels(ctx : Context) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val nm = ctx.getSystemService(classOf[NotificationManager]).asInstanceOf[NotificationManager]
			nm.createNotificationChannel(new NotificationChannel("status",
				ctx.getString(R.string.aprsservice), NotificationManager.IMPORTANCE_LOW))
			nm.createNotificationChannel(new NotificationChannel("msg",
				ctx.getString(R.string.p_msg), NotificationManager.IMPORTANCE_DEFAULT))
		}
	}

  def newNotificationBuilder(ctx : Service, channel : String) : Notification.Builder = {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
			new Notification.Builder(ctx, channel)
		else
			new Notification.Builder(ctx)
  }

	def newNotification(ctx : Service, status : String) : Notification = {
		val i = new Intent(ctx, classOf[APRSdroid])
		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		val appname = ctx.getResources().getString(R.string.app_name)
		val nb = newNotificationBuilder(ctx, "status")
			.setContentTitle(appname)
			.setContentText(status)
			.setContentIntent(PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE))
			.setSmallIcon(R.drawable.ic_status)
			.setWhen(System.currentTimeMillis)
			.setOngoing(true)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
			nb.setShowWhen(true)
		nb.build()
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
		val i = new Intent(ctx, classOf[MessageActivity])
		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		i.setData(Uri.parse(call))
		newNotificationBuilder(ctx, "msg")
			.setContentTitle(call)
			.setContentText(message)
			.setContentIntent(PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
			.setSmallIcon(R.drawable.icon)
			.setTicker(call + ": " + message)
			.setWhen(System.currentTimeMillis)
			.setAutoCancel(true)
			.build()
	}

	def getNotificationMgr(ctx : Context) = ctx.getSystemService(Context.NOTIFICATION_SERVICE).asInstanceOf[NotificationManager]

	def setupNotification(n : Notification, ctx : Context, prefs : PrefsWrapper, default: Boolean, prefix : String) {
		// set notification LED
		if (prefs.getBoolean(prefix + "notify_led", default)) {
			n.ledARGB = Color.YELLOW
			n.ledOnMS = 300
			n.ledOffMS = 1000
			n.flags |= Notification.FLAG_SHOW_LIGHTS
		}
		if (prefs.getBoolean(prefix + "notify_vibr", default)) {
			 ctx.getSystemService(Context.VIBRATOR_SERVICE).asInstanceOf[Vibrator]
				.vibrate(Array[Long](0, 200, 200), -1)
		}
		val sound = prefs.getString(prefix + "notify_ringtone", null)
		if (sound != null)
			n.sound = Uri.parse(sound)
	}

	def notifyMessage(ctx : Service, prefs : PrefsWrapper,
			call : String, message : String) {
		val n = newMessageNotification(ctx, call, message)
		// set notification LED
		setupNotification(n, ctx, prefs, true, "")
		getNotificationMgr(ctx).notify(getCallNumber(call),
			n)
	}

	def cancelMessage(ctx : Context, call : String) {
		getNotificationMgr(ctx).cancel(getCallNumber(call))
	}

	def notifyPosition(ctx : Service, prefs : PrefsWrapper,
			status : String, prefix : String = "pos_") {
		val n = newNotification(ctx, status)
		setupNotification(n, ctx, prefs, false, prefix)
		getNotificationMgr(ctx).notify(SERVICE_NOTIFICATION, n)
	}

	def start(ctx : Service, status : String) = {
		ctx.startForeground(SERVICE_NOTIFICATION, newNotification(ctx, status))
	}

	def stop(ctx : Service) = {
		ctx.stopForeground(true)
	}
}

