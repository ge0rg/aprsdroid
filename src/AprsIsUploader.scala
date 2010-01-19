package de.duenndns.aprsdroid

import _root_.android.location.Location
import _root_.android.content.SharedPreferences

abstract class AprsIsUploader(prefs : SharedPreferences) {
	def start()

	def update(packet : String) : String

	def stop()
}
