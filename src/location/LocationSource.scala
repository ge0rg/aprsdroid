package org.aprsdroid.app

object LocationSource {
	val DEFAULT_CONNTYPE = "smartbeaconing"

	def instanciateLocation(service : AprsService, prefs : PrefsWrapper) : LocationSource = {
		prefs.getString("loc_source", DEFAULT_CONNTYPE) match {
			case "smartbeaconing" => new SmartBeaconing(service, prefs)
			case "periodic" => new PeriodicGPS(service, prefs)
			case "manual" => new FixedPosition(service, prefs)
		}
		
	}
	def instanciatePrefsAct(prefs : PrefsWrapper) = {
		prefs.getString("loc_source", DEFAULT_CONNTYPE) match {
			case "smartbeaconing" => R.xml.location_periodic
			case "periodic" => R.xml.location_periodic
			case "manual" => R.xml.location_manual
		}
	}
}

abstract class LocationSource {
	// the start function might be called multiple times!
	def start(singleShot : Boolean) : String
	def stop()
}
