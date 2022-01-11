package org.aprsdroid.app

import android.app.Application

class APRSdroidApplication extends Application {
	var serviceLocator: ServiceLocator = new ServiceLocatorImpl()
	def setServiceLocator(sl: ServiceLocator): Unit = serviceLocator = sl

	override def onCreate() {
		super.onCreate()
		ServiceNotifier.instance.setupChannels(this)
		MapModes.initialize(this)
	}
}
