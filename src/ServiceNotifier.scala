package org.aprsdroid.app

import _root_.android.app.{Notification, NotificationManager, PendingIntent, Service}
import _root_.android.content.{Context, Intent}
import _root_.android.os.Build


object ServiceNotifier {
	val instance = if (Build.VERSION.SDK.toInt < 5) new DonutNotifier() else new EclairNotifier()
}

abstract class ServiceNotifier {
	val SERVICE_NOTIFICATION : Int = 1

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

	def getNotificationMgr(ctx : Service) = ctx.getSystemService(Context.NOTIFICATION_SERVICE).asInstanceOf[NotificationManager]

	def start(ctx : Service, status : String)
	def stop(ctx : Service)
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

