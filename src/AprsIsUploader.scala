package de.duenndns.aprsdroid

import _root_.android.location.Location

abstract class AprsIsUploader {
	def start()

	def update(host : String, login : String, packet : String) : String

	def stop()
}
