package org.aprsdroid.app
import _root_.android.util.Log
import scala.collection.mutable
import _root_.net.ab0oo.aprs.parser._
import java.util.Date

class DigipeaterService(prefs: PrefsWrapper, TAG: String, sendDigipeatedPacket: String => Unit) {
  private val recentDigipeats: mutable.Map[String, Instant] = mutable.Map()

	def dedupeTime: Int = prefs.getStringInt("p.dedupe", 30)  // Fetch the latest dedupe time from preferences
	def digipeaterpath: String = prefs.getString("digipeater_path", "WIDE1,WIDE2")  // Fetch digipeater path from preferences

	// Function to add or update the digipeat
	def storeDigipeat(sourceCall: String, destinationCall: String, payload: String): Unit = {
	  // Unique identifier using source call, destination call, and payload
	  val key = s"$sourceCall>$destinationCall:$payload"
	  recentDigipeats(key) = new.Date() // Store the current timestamp
	}

	// Function to filter digipeats that are older than dedupeTime seconds
	def isDigipeatRecent(sourceCall: String, destinationCall: String, payload: String): Boolean = {
	  // Unique identifier using source call, destination call, and payload
	  val key = s"$sourceCall>$destinationCall:$payload"
	  recentDigipeats.get(key) match {
		case Some(timestamp) =>
		  // Check if the packet was heard within the last dedupeTime seconds
		  val now = new Date()
		  now.getTime - timestamp.getTime < (dedupeTime * 1000)
		case None =>
		  false // Not found in recent digipeats
	  }
	}

	// Function to clean up old entries
	def cleanupOldDigipeats(): Unit = {
	  val now = new Date()
	  // Retain only those digipeats that are within the last dedupeTime seconds
	  recentDigipeats.retain { case (_, timestamp) =>
		now.getTime - timestamp.getTime < (dedupeTime * 1000)
	  }
	}

	def processIncomingPost(post: String) {
	    Log.d(TAG, "POST STRING TEST: " + post)  // Log the incoming post for debugging

		// Check if backendName contains "KISS" or "AFSK"
		if (prefs.getBackendName().contains("KISS") || prefs.getBackendName().contains("AFSK")) {
			android.util.Log.d("PrefsAct", "Backend contains KISS or AFSK")
		} else {
			android.util.Log.d("PrefsAct", "Backend does not contain KISS or AFSK")
			return
		}	
		//TODO, Add workaround for unsupported formats.
		// Attempt to parse the incoming post to an APRSPacket.
		val packet = try {
			Parser.parse(post) // Attempt to parse
		} catch {
			case e: Exception =>
				Log.e("Parsing FAILED!", s"Failed to parse packet: $post", e)
				return // Exit the function if parsing fails
		}

		// New regen
		if (!prefs.isDigipeaterEnabled() && prefs.isRegenerateEnabled()) {
			Log.d("APRSdroid.Service", "Regen enabled")
			sendDigipeatedPacket(packet.toString)
			return // Exit if both digipeating and regeneration are enabled
		}	
			
		// Check if the digipeating setting is enabled
		if (!prefs.isDigipeaterEnabled()) {
				Log.d("APRSdroid.Service", "Digipeating is disabled; skipping processing.")
				return // Exit if digipeating is not enabled
		}	

		cleanupOldDigipeats() // Clean up old digipeats before processing

		// Try to parse the incoming post to an APRSPacket
		try {
			// Now you can access the source call from the packet
			val callssid = prefs.getCallSsid()			
			val sourceCall = packet.getSourceCall()
			val destinationCall = packet.getDestinationCall();
			val lastUsedDigi = packet.getDigiString()
			val payload = packet.getAprsInformation()	

			val payloadString = packet.getAprsInformation().toString() // Ensure payload is a String


			// Check if callssid matches sourceCall; if they match, do not digipeat
			if (callssid == sourceCall) {
				Log.d("APRSdroid.Service", s"No digipeat: callssid ($callssid) matches source call ($sourceCall).")
				return // Exit if no digipeating is needed
			}				
			
			// Check if this packet has been digipeated recently
			if (isDigipeatRecent(sourceCall, destinationCall, payloadString)) {
			Log.d("APRSdroid.Service", s"Packet from $sourceCall to $destinationCall and $payload has been heard recently, skipping digipeating.")
			  return // Skip processing this packet
			}			
						
			
			val (modifiedDigiPath, digipeatOccurred) = processDigiPath(lastUsedDigi, callssid)			


			Log.d("APRSdroid.Service", s"Source: $sourceCall")
			Log.d("APRSdroid.Service", s"Destination: $destinationCall")
			Log.d("APRSdroid.Service", s"Digi: $lastUsedDigi")
			Log.d("APRSdroid.Service", s"Modified Digi Path: $modifiedDigiPath")
			
			Log.d("APRSdroid.Service", s"Payload: $payload")

			// Format the string for sending
			val digipeatedPacket = s"$sourceCall>$destinationCall,$modifiedDigiPath:$payload"

			// Optionally, send a test packet with the formatted string only if a digipeat occurred
			if (digipeatOccurred) {
				sendDigipeatedPacket(digipeatedPacket)
								
				// Store the digipeat to the recent list
				storeDigipeat(sourceCall, destinationCall, payloadString)				
				
			} else {
				Log.d("APRSdroid.Service", "No digipeat occurred, not sending a test packet.")
			}		
			
		} catch {
			case e: Exception =>
				Log.e("APRSdroid.Service", s"Failed to parse packet: $post", e)
		}
	}

	def processDigiPath(lastUsedDigi: String, callssid: String): (String, Boolean) = {
		// Log the input Digi path
		Log.d("APRSdroid.Service", s"Original Digi Path: '$lastUsedDigi'")

		// If lastUsedDigi is empty, return it unchanged
		if (lastUsedDigi.trim.isEmpty) {
			Log.d("APRSdroid.Service", "LastUsedDigi is empty, returning unchanged.")
			return (lastUsedDigi, false)
		}

		// Remove leading comma for easier processing
		val trimmedPath = lastUsedDigi.stripPrefix(",")

		// Split the path into components, avoiding empty strings
		val pathComponents = trimmedPath.split(",").toList.filter(_.nonEmpty)
		val digipeaterPaths = digipeaterpath.split(",").toList.filter(_.nonEmpty)

		// Create a new list of components with modifications
		val (modifiedPath, modified) = pathComponents.foldLeft((List.empty[String], false)) {
			case ((acc, hasModified), component) =>
			
				// Check if callssid* is in the path and skip if found
				if (component == s"$callssid*") {
					// Skip digipeating if callssid* is found
					return (lastUsedDigi, false) // Return the original path, do not modify
									
				} else if (!hasModified && (digipeaterPaths.exists(path => component.split("-")(0) == path) || digipeaterPaths.contains(component) || component == callssid)) {
					// We need to check if the first unused component matches digipeaterpath
					if (acc.isEmpty || acc.last.endsWith("*")) {
						// This is the first unused component
						component match {
	
						  case w if w.matches(".*-(\\d+)$") =>
							// Extract the number from the suffix
							val number = w.split("-").last.toInt
							// Decrement the number
							val newNumber = number - 1 
														
							if (newNumber == 0 || w == callssid) {
							  // If the number is decremented to 0, remove the component and insert callssid*
							  (acc :+ s"$callssid*", true)
							  
							} else {
							  // Otherwise, decrement the number and keep the component
							  val newComponent = w.stripSuffix(s"-$number") + s"-$newNumber"
							  (acc :+ s"$callssid*" :+ newComponent, true)
							}

						  case _ =>
							// Leave unchanged if there's no -N suffix
							(acc :+ component, hasModified)
						}

					} else {
						// If the first unused component doesn't match digipeaterpath, keep unchanged
						(acc :+ component, hasModified)
					}
				
				} else {
					// Keep the component as it is
					(acc :+ component, hasModified)
				}
		}

		// Rebuild the modified path
		val resultPath = modifiedPath.mkString(",")

		// Log the modified path before returning
		Log.d("APRSdroid.Service", s"Modified Digi Path: '$resultPath'")

		// If no modification occurred, return the original lastUsedDigi
		if (resultPath == trimmedPath) {
			Log.d("APRSdroid.Service", "No modifications were made; returning the original path.")
			return (lastUsedDigi, false)
		}

		// Return the modified path with a leading comma
		(s"$resultPath", true)
	}
	
}