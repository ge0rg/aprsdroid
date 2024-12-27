package org.aprsdroid.app
import _root_.android.content.Context

import _root_.java.io.{BufferedReader, InputStreamReader, OutputStream, PrintWriter, IOException}
import _root_.java.net.{Socket, SocketException}
import _root_.android.util.Log
import _root_.net.ab0oo.aprs.parser._
import scala.collection.mutable
import java.util.Date

// Define a callback interface for connection events
trait ConnectionListener {
  def onConnectionLost(): Unit
}

class IgateService(service: AprsService, prefs: PrefsWrapper) extends ConnectionListener {

  val TAG = "IgateService"
  val hostport = prefs.getString("p.igserver", "rotate.aprs2.net")
  val (host, port) = parseHostPort(hostport)  
  val so_timeout = prefs.getStringInt("p.igsotimeout", 120)
  val connectretryinterval = prefs.getStringInt("p.igconnectretry", 30)
  var conn: TcpSocketThread = _
  var reconnecting = false  // Track if we're reconnecting

  // Parse host and port from the hostport string
  def parseHostPort(hostport: String): (String, Int) = {
  val parts = hostport.split(":")
    if (parts.length == 2) {
   	  (parts(0), parts(1).toInt)
	} else {
  	  (hostport, 14580)  // default port
    }
  }

  // Start the connection
  def start(): Unit = {
    Log.d(TAG, s"start() - Starting connection to $host:$port")
    if (conn == null) {
      Log.d(TAG, "start() - No existing connection, creating new connection.")
      createConnection()
    } else {
      Log.d(TAG, "start() - Connection already exists.")
    }
  }

  // Create the TCP connection and pass 'this' as listener
  def createConnection(): Unit = {
    Log.d(TAG, s"createConnection() - Connecting to $host:$port")
    conn = new TcpSocketThread(host, port, so_timeout, service, prefs, this)  // Pass host and port
    Log.d(TAG, "createConnection() - TcpSocketThread created, starting thread.")
    conn.start()
  }

  // Stop the connection
  def stop(): Unit = {
    Log.d(TAG, "stop() - Stopping connection")
    if (conn != null) {
      conn.synchronized {
        conn.running = false
      }
      Log.d(TAG, "stop() - Waiting for connection thread to join.")
      conn.join(50)
      conn.shutdown()  // Make sure the socket is cleanly closed
      Log.d(TAG, "stop() - Connection shutdown.")
    } else {
      Log.d(TAG, "stop() - No connection to stop.")
    }
  }

  // Handle and submit data to the server via TcpSocketThread
  def handlePostSubmitData(data: String): Unit = {
    Log.d(TAG, s"handlePostSubmitData() - Received data: $data")
    if (conn != null) {
      Log.d(TAG, "handlePostSubmitData() - Delegating data to TcpSocketThread.")
      conn.handlePostSubmitData(data)
    } else {
      Log.d(TAG, "handlePostSubmitData() - No active connection to send data.")
    }
  }

  // External reconnect logic
  def reconnect(): Unit = {
    Log.d(TAG, "reconnect() - Initiating reconnect.")
    
	// Check if the service is already running (get the value of the "service_running" preference)
	val service_running = prefs.getBoolean("service_running", false) // Default to false if not set
	  
	// If the service is already running, don't proceed
	if (!service_running) {
	  Log.d(TAG, "start() - Service is not running, skipping connection.")
	  reconnecting = false
	  return
	}
	
    if (reconnecting) {
      Log.d(TAG, "reconnect() - Already in reconnecting process, skipping.")
      return
    }
    
    reconnecting = true
    
	service.addPost(StorageDatabase.Post.TYPE_INFO, "APRS-IS", s"Connection lost... Reconnecting in $connectretryinterval seconds")

    // Step 1: Stop the current connection
    stop()
    
    // Step 2: Wait for a while before reconnecting
    Thread.sleep(connectretryinterval * 1000) // Wait for 5 seconds before reconnect attempt (can be adjusted)
    
    // Step 3: Create a new connection
    Log.d(TAG, "reconnect() - Attempting to create a new connection.")
    createConnection()
    
    reconnecting = false
  }

  // Callback implementation when the connection is lost
  override def onConnectionLost(): Unit = {
    Log.d(TAG, "onConnectionLost() - Connection lost, attempting to reconnect.")
    reconnect() // Automatically reconnect
  }
}

class TcpSocketThread(host: String, port: Int, timeout: Int, service: AprsService, prefs: PrefsWrapper, listener: ConnectionListener) extends Thread {
  @volatile var running = true
  private var socket: Socket = _
  private var reader: BufferedReader = _
  private var writer: PrintWriter = _
  
  // Track the time of the last sent packets
  private val sentPackets1Min: mutable.Queue[Long] = mutable.Queue()
  private val sentPackets5Min: mutable.Queue[Long] = mutable.Queue()
  private val mspMap: mutable.HashMap[String, Int] = mutable.HashMap[String, Int]() 

  // Assuming we have a Map to store the source calls and their last heard timestamps
  val lastHeardCalls: mutable.Map[String, Long] = mutable.Map()

  override def run(): Unit = {
    Log.d("IgateService", s"run() - Starting TCP connection to $host with timeout $timeout")
	service.addPost(StorageDatabase.Post.TYPE_INFO, "APRS-IS", s"Connecting to $host:$port")

    while (running) {
      try {
        // Establish the connection
        socket = new Socket(host, port)
        socket.setSoTimeout(timeout * 1000)
        Log.d("IgateService", s"run() - Connected to $host")

        reader = new BufferedReader(new InputStreamReader(socket.getInputStream))
        writer = new PrintWriter(socket.getOutputStream, true)
        sendLogin()
		service.addPost(StorageDatabase.Post.TYPE_INFO, "APRS-IS", "Connected to APRS-IS")


        while (running) {
          val message = reader.readLine()
          if (message != null) {
            Log.d("IgateService", s"run() - Received message: $message")
            
		    handleMessage(message)
			handleAprsTrafficPost(message)
						 
		  } else {
            Log.d("IgateService", "run() - Server disconnected. Attempting to reconnect.")
            running = false
            listener.onConnectionLost() // Notify listener (IgateService) of failure
          }
        }

      } catch {
        case e: SocketException =>
          Log.e("IgateService", s"run() - SocketException: ${e.getMessage}")
          running = false
          listener.onConnectionLost() // Notify listener (IgateService) of failure
        case e: IOException =>
          Log.e("IgateService", s"run() - IOException: ${e.getMessage}")
          running = false
          listener.onConnectionLost() // Notify listener (IgateService) of failure
      } finally {
        shutdown() // Ensure resources are cleaned up
      }
    }
  }

  // Send login information to the APRS-IS server
  def sendLogin(): Unit = {
    Log.d("IgateService", "sendLogin() - Sending login information to server.")
    val callsign = prefs.getCallSsid()
    val passcode = prefs.getPasscode()  // Retrieve passcode from preferences
    val version = s"APRSdroid ${service.APP_VERSION.filter(_.isDigit).takeRight(2).mkString.split("").mkString(".")}"
    val filter = prefs.getString("p.igfilter", "")

    // Format the login message as per the Python example
    val loginMessage = s"user $callsign pass $passcode vers $version\r\n"
    val filterMessage = s"#filter $filter\r\n"  // Retrieve filter from preferences

    Log.d("IgateService", s"sendLogin() - Sending login: $loginMessage")
    Log.d("IgateService", s"sendLogin() - Sending filter: $filterMessage")

    // Send the login message to the server
    writer.println(loginMessage)
    writer.flush()
    Log.d("IgateService", "sendLogin() - Login sent.")

    // Send the filter command
    writer.println(filterMessage)
    writer.flush()
    Log.d("IgateService", "sendLogin() - Filter sent.")
  }

  // Modify data string before sending it
  def modifyData(data: String): String = {
    Log.d("IgateService", s"modifyData() - Received data: $data")
    // Check if data contains "RFONLY" or "TCPIP"
    if (data.contains("RFONLY") || data.contains("TCPIP") || data.contains("NOGATE")) {
      Log.d("IgateService", s"modifyData() - RFONLY or TCPIP found: $data")
      return null // Return null if the packet contains "RFONLY" or "TCPIP"
    }
    
    // Find the index of the first colon
    val colonIndex = data.indexOf(":")
    Log.d("IgateService", s"modifyData() - Colon index: $colonIndex")

	// Set qconstruct based on whether bidirectional gate is enabled in preferences
	val qconstruct = if (prefs.getBoolean("p.aprsistorf", false)) "qAR" else "qAO"

    if (colonIndex != -1) {
      // Insert ",qAR, or ,qAS," before the first colon
      val modifiedData = data.substring(0, colonIndex) + s",$qconstruct," + prefs.getCallSsid + data.substring(colonIndex)
      Log.d("IgateService", s"modifyData() - Modified data: $modifiedData")
      return modifiedData
    } else {
      // If there's no colon, return the data as is (or handle this case as needed)
      Log.d("IgateService", "modifyData() - No colon found, returning data as is.")
      return data
    }
  }

  // Handle modified data before submitting it to the server
  def handlePostSubmitData(data: String): Unit = {
    //Log.d("IgateService", s"handlePostSubmitData() - Received data: $data")

    // Modify the data before sending it to the server
    val modifiedData = modifyData(data)

    // If the modified data is null, skip further processing
    if (modifiedData == null) {
      Log.d("IgateService", "handlePostSubmitData() - Skipping data processing due to RFONLY/TCPIP in packet")
      return  // Stop further processing if the packet contains RFONLY or TCPIP
    }

    // Extract the callsign from the modified data (before the '>' symbol)
    val callSign = modifiedData.split(">")(0).trim  // Split the string and take the part before '>'

    // Update lastHeardCalls map with the current timestamp for that callsign
    lastHeardCalls(callSign) = System.currentTimeMillis()  // Use current time in milliseconds
    Log.d("IgateService", s"handlePostSubmitData() - Extracted callsign: $callSign, updating last heard time to ${System.currentTimeMillis()} for that callsign.")

    // Log the modified data to confirm the change
    Log.d("IgateService", s"handlePostSubmitData() - Modified data: $modifiedData")

    // Send the modified data to the APRS-IS server (or other logic as necessary)
    if (socket != null && socket.isConnected) {
      sendData(modifiedData)  // Send it to the server
      Log.d("IgateService", "handlePostSubmitData() - Data sent to server.")
      service.addPost(StorageDatabase.Post.TYPE_IG, "APRS-IS Sent", modifiedData)
    } else {
      Log.e("IgateService", "handlePostSubmitData() - No active connection to send data.")
    }
  }

  // Send data to the server
  def sendData(data: String): Unit = {
    Log.d("IgateService", s"sendData() - Sending data: $data")
    if (writer != null) {
      writer.println(data)
      writer.flush()
    } else {
      Log.e("IgateService", "sendData() - Writer is null, cannot send data.")
    }
  }

  // Clean up resources
  def shutdown(): Unit = {
    if (socket != null) {
      try {
        socket.close()
      } catch {
        case e: IOException => Log.e("IgateService", s"shutdown() - Error closing socket: ${e.getMessage}")
      }
    }
  }
  
  def handleAprsTrafficPost(message: String): Unit = {
    val aprsIstrafficEnabled = prefs.getBoolean("p.aprsistraffic", false)
  	
    if (!aprsIstrafficEnabled) {
  	// If the checkbox is enabled, perform the action
  	if (message.startsWith("#")) {
  		service.addPost(StorageDatabase.Post.TYPE_INFO, "APRS-IS", message)
  		Log.d("IgateService", s"APRS-IS traffic enabled, post added: $message")
  	} else {
  		service.addPost(StorageDatabase.Post.TYPE_IG, "APRS-IS Received", message)
  		Log.d("IgateService", s"APRS-IS traffic enabled, post added: $message")	
  	}
    } else {
  	// If the checkbox is not enabled, skip the action
  	Log.d("IgateService", "APRS-IS traffic disabled, skipping the post.")
  	return
    }
  }	 
  
  def processMessage(payloadString: String): String = {
	//Check if payload is actually a message and not telemetry
	if (payloadString.startsWith(":")) {
	  if (payloadString.length < 11 || 
	  	payloadString.length >= 16 && 
	  	(payloadString.substring(10).startsWith(":PARM.") ||
	  	 payloadString.substring(10).startsWith(":UNIT.") ||
	  	 payloadString.substring(10).startsWith(":EQNS.") ||
	  	 payloadString.substring(10).startsWith(":BITS."))) {
		return null // Ignore this payload
	  }
	  if (payloadString.length >= 4 && 
	  	(payloadString.substring(1, 4) == "BLN" ||
	  	 payloadString.substring(1, 4) == "NWS" ||
	  	 payloadString.substring(1, 4) == "SKY" ||
	  	 payloadString.substring(1, 4) == "CWA" ||
	  	 payloadString.substring(1, 4) == "BOM")) {
	  return null // Ignore unwanted prefixes
	  }
	  payloadString.stripPrefix(":").takeWhile(_ != ':').replaceAll("\\s", "")
	} else {
	  null // No targeted callsign if it doesn't start with ":". Not a message.
	}
  }

  def processPacketPosition(fap: APRSPacket): String = {
    //Process APRS-IS Packet for RF destination
    try {
	  val callssid = prefs.getCallSsid() // Get local call sign from preferences
	  val sourceCall = fap.getSourceCall()  // Source callsign from fap
	  val destinationCall = fap.getDestinationCall()	// Destination callsign
	  val lastUsedDigi = fap.getDigiString()	// Last used digipeater
	  val payload = fap.getAprsInformation()	// Payload of the message
	  val payloadString = if (payload != null) payload.toString else ""
	  val digipath = prefs.getString("igpath", "WIDE1-1")	 
	  val formattedDigipath = if (digipath.nonEmpty) s",$digipath" else ""
	  val version = service.APP_VERSION	// Version information

	  // If targetedCallsign is null, check MSP for sourceCall
	  if (mspMap.getOrElse(sourceCall, 0) == 1) { 
	    Log.d("IgateService", s"MSP entry found and is 1 for $sourceCall, pass packet")
	
	    // If MSP for sourceCall is 1, process the packet and remove the entry
	    mspMap.remove(sourceCall) 
	  
	    // Process and create the packet
	    val igatedPacket = s"$callssid>$version$formattedDigipath:}$sourceCall>$destinationCall,TCPIP,$callssid*:$payload"
	    Log.d("IgateService", s"Processed packet: $igatedPacket")

	    // Call checkRateLimit and return null if rate limit exceeded
	    if (checkRateLimit()) {
	      // Log when the rate limit is exceeded and the packet is skipped
	      Log.d("IgateService", "Rate limit exceeded, skipping this packet.")
	      return null // Skip sending this packet if rate limit exceeded
		}
	    
	    // Handle rate limiting to update the queues
	    handleRateLimiting()
    
	    return igatedPacket
	  
	  } else {
	    Log.d("IgateService", s"Station not MSP, skipping processing.")
	    return null
	  }
		
    } catch {
  	case e: Exception =>
  	  Log.e("IgateService", s"processPacketPostion() - Error processing packet", e)
  	  return null
	}
  }		

  def processPacketMessage(fap: APRSPacket): String = {
    //Process APRS-IS Packet for RF destination
    val timelastheard = prefs.getStringInt("p.timelastheard", 30)
    try {
	  val callssid = prefs.getCallSsid() // Get local call sign from preferences
	  val sourceCall = fap.getSourceCall()  // Source callsign from fap
	  val destinationCall = fap.getDestinationCall()	// Destination callsign
	  val lastUsedDigi = fap.getDigiString()	// Last used digipeater
	  val payload = fap.getAprsInformation()	// Payload of the message
	  val payloadString = if (payload != null) payload.toString else ""
	  val digipath = prefs.getString("igpath", "WIDE1-1")	 
	  val formattedDigipath = if (digipath.nonEmpty) s",$digipath" else ""
	  val version = service.APP_VERSION	// Version information
	  
	  val targetedCallsign = processMessage(payloadString)
	  Log.d("IgateService", s"Targeted Callsign: $targetedCallsign")
	  
	  // If the targetedCallsign is null, return immediately and skip further processing
      if (targetedCallsign == null) {
        Log.d("IgateService", "Target station not found or not a message packet, skipping packet processing.")
        return null
      }

	  // Check if we have seen this source call in the last 30 minutes
	  val currentTime = System.currentTimeMillis()
	  //val lastHeardTime = lastHeardCalls.getOrElse(Option(targetedCallsign).getOrElse(sourceCall), 0L) //Checks RF station first, then checks APRS-IS station
	  val lastHeardTime = lastHeardCalls.getOrElse(targetedCallsign, 0L)
	  val timeElapsed = currentTime - lastHeardTime
	  
	  Log.d("IgateService", s"processPacketMessage() - $targetedCallsign, last heard at $lastHeardTime, time elapsed: $timeElapsed ms.")
	  
	  if (timeElapsed <= timelastheard * 60 * 1000) { // If it was heard within the last 30 minutes
		//Set MSP for originating sourceCall that is messaging the targetedCallsign
		mspMap.getOrElseUpdate(sourceCall, 1)
	    Log.d("IgateService", s"MSP set to 1 for $sourceCall")
	    
		// Process and create the packet
	    val igatedPacket = s"$callssid>$version$formattedDigipath:}$sourceCall>$destinationCall,TCPIP,$callssid*:$payload"
	    Log.d("IgateService", s"Processed packet: $igatedPacket")

	    // Call checkRateLimit and return null if rate limit exceeded
	    if (checkRateLimit()) {
	      // Log when the rate limit is exceeded and the packet is skipped
	      Log.d("IgateService", "Rate limit exceeded, skipping this packet.")
	      return null // Skip sending this packet if rate limit exceeded
		}
	    
	    // Handle rate limiting to update the queues
	    handleRateLimiting()
    
	    return igatedPacket
	  
	  } else {
	    Log.d("IgateService", s"Station not heard recently, skipping processing.")
	    return null
	  }
		
    } catch {
  	case e: Exception =>
  	  Log.e("IgateService", s"processPacketMessage() - Error processing packet", e)
  	  return null
	}
  }		

	// Function to handle adding time to the queues and enforcing rate limits
  def handleRateLimiting(): Unit = {
	val currentTime = System.currentTimeMillis()
		
	// Log before adding the current time to the queues
	Log.d("IgateService", s"Adding current time to queues: $currentTime")

	// Add the current time to both 1-minute and 5-minute queues
	sentPackets1Min.enqueue(currentTime)
	sentPackets5Min.enqueue(currentTime)

	// Get the rate limit values from preferences for 1 minute and 5 minutes
	val limit1Min = prefs.getStringInt("p.ratelimit1", 6)
	val limit5Min = prefs.getStringInt("p.ratelimit5", 10)

	// If the queues exceed the limit, remove the oldest timestamp
	if (sentPackets1Min.size > limit1Min) {
		sentPackets1Min.dequeue()
	}
	if (sentPackets5Min.size > limit5Min) {
		sentPackets5Min.dequeue()
	}

	// Log the sizes of the queues after the new time is added
	Log.d("IgateService", s"sentPackets1Min size after enqueue: ${sentPackets1Min.size}")
	Log.d("IgateService", s"sentPackets5Min size after enqueue: ${sentPackets5Min.size}")
  }
  
  def checkRateLimit(): Boolean = {
	val currentTime = System.currentTimeMillis() // Get the current time in milliseconds

	// Log the current time for debugging purposes
	Log.d("IgateService", s"Current time: $currentTime")

    // Remove packets older than 1 minute (60,000 ms)
    sentPackets1Min.dequeueAll(packetTime => currentTime - packetTime > 60000)
    // Log the size of the queue after dequeuing old packets
    Log.d("IgateService", s"sentPackets1Min size after cleanup: ${sentPackets1Min.size}")

    // Remove packets older than 5 minutes (300,000 ms)
    sentPackets5Min.dequeueAll(packetTime => currentTime - packetTime > 300000)	  
    // Log the size of the queue after dequeuing old packets
    Log.d("IgateService", s"sentPackets5Min size after cleanup: ${sentPackets5Min.size}")

    // Get the rate limit value from preferences for 1 minute (default is 6 if not set)
    val limit1Min = prefs.getStringInt("p.ratelimit1", 6)
    // Get the rate limit value from preferences for 5 minutes (default is 10 if not set)
    val limit5Min = prefs.getStringInt("p.ratelimit5", 10)

    // Check if the packet limits for 1 minute and 5 minutes are exceeded
    if (sentPackets1Min.size >= limit1Min) {
	  Log.w("IgateService", "Packet limit exceeded for 1 minute interval. Dropping packet.")
	  service.addPost(StorageDatabase.Post.TYPE_ERROR, "APRS-IS > RF", "Packet limit exceeded for 1 minute. Packet dropped.")
	  // Log the state when rate limit is exceeded
	  Log.d("IgateService", "Rate limit exceeded for 1 minute. Returning true.")
	  return true // Return true to indicate rate limit exceeded
    }

    if (sentPackets5Min.size >= limit5Min) {
	  Log.w("IgateService", "Packet limit exceeded for 5 minute interval. Dropping packet.")
	  service.addPost(StorageDatabase.Post.TYPE_ERROR, "APRS-IS > RF", "Packet limit exceeded for 5 minutes. Packet dropped.")
	  // Log the state when rate limit is exceeded
	  Log.d("IgateService", "Rate limit exceeded for 5 minutes. Returning true.")
	  return true // Return true to indicate rate limit exceeded
    }

    // Log when no rate limit is exceeded
    Log.d("IgateService", "No rate limit exceeded. Returning false.")
    return false // Return false to indicate no rate limit exceeded
  }

  def handleMessage(message: String): Unit = {
    // Early return if message starts with '#'
    if (message.startsWith("#")) {
  	Log.d("IgateService", "Message starts with '#', skipping processing.")
  	return
    }		
    Log.d("IgateService", s"handleMessage() - Handling incoming message: $message")
  
    // Check if bidirectional gate is enabled in preferences
    val bidirectionalGate = prefs.getBoolean("p.aprsistorf", false)
  
    if (!bidirectionalGate) {
  	Log.d("IgateService", "Bidirectional IGate disabled.")		
  	return
    }	
  
    // Attempt to parse the message
    try {
  	// Attempt to parse the incoming message using the Parser
  	val fap = Parser.parse(message) 
  	Log.d("IgateService", s"Packet type: ${fap.getAprsInformation.getClass.getSimpleName}")
  
  	// Check the type of the parsed packet
  	fap.getAprsInformation() match {
  	  case msg: MessagePacket =>
  		// Process MessagePacket
  		try {
  		  val igatedPacket = processPacketMessage(fap) // Process and create the igated packet
  		  
  		  if (igatedPacket != null) {
  			Log.d("IgateService", s"Sending igated packet: $igatedPacket")
  			service.sendThirdPartyPacket(igatedPacket) // Send the packet to the third-party service
  		  } else {
  			Log.d("IgateService", "Packet not processed, skipping send.")
  		  }
  		} catch {
  		  case e: Exception =>
  			Log.e("IgateService", s"Error processing MessagePacket: ${e.getMessage}")
  		}
  
  	  case msg: PositionPacket =>
  		// Process PositionPacket
  		try {
  		  val igatedPacket = processPacketPosition(fap) // Process and create the igated packet
  		  
  		  if (igatedPacket != null) {
  			Log.d("IgateService", s"Sending igated packet: $igatedPacket")
  			service.sendThirdPartyPacket(igatedPacket) // Send the packet to the third-party service
  		  } else {
  			Log.d("IgateService", "Packet not processed, skipping send.")
  		  }
  		} catch {
  		  case e: Exception =>
  			Log.e("IgateService", s"Error processing PositionPacket: ${e.getMessage}")
  		}
  
  	  case _ =>
  		// If it's not a MessagePacket or PositionPacket, skip processing
  		Log.d("IgateService", s"handleMessage() - Not a MessagePacket or PositionPacket, skipping processing.")
  	}
    } catch {
  	case e: Exception =>
  	  Log.e("IgateService", s"handleMessage() - Failed to parse packet: $message", e)
    }
  }
}
