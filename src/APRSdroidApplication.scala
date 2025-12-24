package org.aprsdroid.app

import android.app.Application

class APRSdroidApplication extends Application {
	var serviceLocator: ServiceLocator = null
	def setServiceLocator(sl: ServiceLocator): Unit = serviceLocator = sl

	override def onCreate() {
		super.onCreate()
		setServiceLocator(new ServiceLocatorImpl())
		ServiceNotifier.instance.setupChannels(this)
		MapModes.initialize(this)
	}
}
