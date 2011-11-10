package org.aprsdroid.app

object LocationSource {
	val DEFAULT_CONNTYPE = "smartbeaconing"

	def instanciateLocation(service : AprsService, prefs : PrefsWrapper) : LocationSource = {
		prefs.getString("loc_source", DEFAULT_CONNTYPE) match {
			case "smartbeaconing" => new SmartBeaconing(service, prefs)
			case "periodic" => new PeriodicGPS(service, prefs)
			case "fixed" => new FixedPosition(service, prefs)
		}
		
	}
	def instanciatePrefsAct(prefs : PrefsWrapper) = {
	}
}

abstract class LocationSource {
	def start(singleShot : Boolean)
	def restart()
	def stop()
}
