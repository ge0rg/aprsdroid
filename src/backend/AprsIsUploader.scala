package de.duenndns.aprsdroid

import _root_.android.location.Location

abstract class AprsIsUploader(host : String, login : String) {
	def start()

	def update(packet : String) : String

	def stop()
}
