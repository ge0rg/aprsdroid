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
    if (data.contains("RFONLY") || data.contains("TCPIP")) {
      Log.d("IgateService", s"modifyData() - RFONLY or TCPIP found: $data")
      return null // Return null if the packet contains "RFONLY" or "TCPIP"
    }
    
    // Find the index of the first colon
    val colonIndex = data.indexOf(":")
    Log.d("IgateService", s"modifyData() - Colon index: $colonIndex")

    if (colonIndex != -1) {
      // Insert ",qAR" before the first colon
      val modifiedData = data.substring(0, colonIndex) + ",qAR," + prefs.getCallSsid + data.substring(colonIndex)
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
		service.addPost(StorageDatabase.Post.TYPE_IG, "APRS-IS Received", message)
		Log.d("IgateService", s"APRS-IS traffic enabled, post added: $message")
	  } else {
		// If the checkbox is not enabled, skip the action
		Log.d("IgateService", "APRS-IS traffic disabled, skipping the post.")
		return
	  }
	}
	  
  
	def handleMessage(message: String): Unit = {
	  // Early return if message starts with '#'
	  if (message.startsWith("#")) {
		Log.d("IgateService", "Message starts with '#', skipping processing.")
		return
	  }		
	  Log.d("IgateService", s"handleMessage() - Handling incoming message: $message")
      val bidirectionalGate = prefs.getBoolean("p.aprsistorf", false)
	  val timelastheard = prefs.getStringInt("p.timelastheard", 30)  

	  if (!bidirectionalGate) {
	    Log.d("IgateService", "Bidirectional IGate disabled.")		
	    return
	  }	

	  // Attempt to parse the message
	  val packetOpt = try {
		val fap = Parser.parse(message) // Attempt initial parsing

		// Check if it's a MessagePacket (i.e., a message type)
		fap.getAprsInformation() match {
		  case msg: MessagePacket =>
			// Only proceed with further processing if it's a MessagePacket
			try {
			  val callssid = prefs.getCallSsid() // Get the local call sign from preferences
			  val sourceCall = fap.getSourceCall()  // Use fap (parsed packet)
			  val destinationCall = fap.getDestinationCall()  // Use fap
			  val lastUsedDigi = fap.getDigiString()  // Use fap
			  val payload = fap.getAprsInformation()
			  val payloadString = if (payload != null) payload.toString else ""
			  val digipath = prefs.getString("igpath", "WIDE1-1")	
			  val formattedDigipath = if (digipath.nonEmpty) s",$digipath" else ""
			  val version = service.APP_VERSION   // Version information (as in Python example)

			  // Extract the targeted callsign by stripping leading and trailing colons and removing spaces
			  val targetedCallsign = payloadString
			    .stripPrefix(":")               // Remove any leading colon
			    .takeWhile(_ != ':')            // Take everything up to the first colon
			    .replaceAll("\\s", "")          // Remove any spaces

			  Log.d("IgateService", s"Targeted Callsign: $targetedCallsign")
							
			  Log.d("IgateService", s"handleMessage() - Parsed Packet Info:")
			  Log.d("IgateService", s"Source Call: $sourceCall")
			  Log.d("IgateService", s"Destination Call: $destinationCall")
			  Log.d("IgateService", s"Last Used Digi: $lastUsedDigi")
			  Log.d("IgateService", s"Payload: $payloadString")

			  // Check if we have seen this source call in the last 30 minutes
			  val currentTime = System.currentTimeMillis()
			  val lastHeardTime = lastHeardCalls.getOrElse(targetedCallsign, 0L)
			  val timeElapsed = currentTime - lastHeardTime
			  Log.d("IgateService", s"handleMessage() - $targetedCallsign, " + s"last heard at $lastHeardTime, time elapsed: $timeElapsed ms.")

			  if (timeElapsed <= timelastheard * 60 * 1000) { // If it was heard within 30 minutes
				// Process and send the packet
				val igatedPacket = s"$callssid>$version$formattedDigipath:}$sourceCall>$destinationCall,TCPIP,$callssid*:$payload"
				Log.d("IgateService", igatedPacket)
				service.sendThirdPartyPacket(igatedPacket) // Send the packet
			  } else {
				Log.d("IgateService", s"handleMessage() - Source call $sourceCall has not been heard in the last 30 minutes, skipping processing.")
			  }

			} catch {
			  case e: Exception =>
				Log.e("IgateService", s"handleMessage() - Error processing parsed packet: $message", e)
			}
		  case _ =>
			// If it's not a MessagePacket, don't process further
			Log.d("IgateService", s"handleMessage() - Not a MessagePacket, skipping processing.")
		}
	  } catch {
		case e: Exception =>
		  Log.e("IgateService", s"handleMessage() - Failed to parse packet: $message", e)
	  }
	}

}
