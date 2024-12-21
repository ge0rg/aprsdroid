package org.aprsdroid.app
import _root_.android.content.Context

import _root_.java.io.{BufferedReader, InputStreamReader, OutputStream, PrintWriter, IOException}
import _root_.java.net.{Socket, SocketException}
import _root_.android.util.Log

class IgateService(service : AprsService, prefs: PrefsWrapper) {

  val TAG = "IgateService"
  val hostport = prefs.getString("tcp.server", "theconnectdesk.com")
  val so_timeout = prefs.getStringInt("tcp.sotimeout", 30)
  var conn: TcpSocketThread = _

  // Start the connection
  def start(): Unit = {
    Log.d(TAG, "start() - Starting connection to $hostport")
    if (conn == null)
      createConnection()
  }

  // Create the TCP connection
  def createConnection(): Unit = {
    Log.d(TAG, s"createConnection() - Connecting to $hostport")
    conn = new TcpSocketThread(hostport)
    conn.start()
  }

  // Stop the connection
  def stop(): Unit = {
    if (conn != null) {
      Log.d(TAG, "stop() - Stopping connection")
      conn.synchronized {
        conn.running = false
      }
      conn.interrupt()
      conn.join(50)
    }
  }

  // Thread responsible for managing the TCP connection
  class TcpSocketThread(hostport: String) extends Thread("IgateService TCP connection") {
    var running = true
    var socket: Socket = _
    var outputStream: OutputStream = _
    var printWriter: PrintWriter = _
    var bufferedReader: BufferedReader = _

	// Initialize the socket
	def init_socket(): Unit = {
	  val (host, port) = parseHostPort(hostport)
	  Log.d(TAG, s"init_socket() - Connecting to $host on port $port")

	  var attempts = 0
	  while (running) {
		try {
		  socket = new Socket(host, port)
		  socket.setKeepAlive(true)
		  socket.setSoTimeout(so_timeout * 1000)
		  outputStream = socket.getOutputStream
		  printWriter = new PrintWriter(outputStream, true)
		  bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream))

		  // Send login information to the server
		  sendLogin()

		  Log.d(TAG, "init_socket() - Connection established")
		  return  // If connection is successful, exit the loop
		} catch {
		  case e: java.net.UnknownHostException =>
			// DNS resolution failed, will retry
			attempts += 1
			Log.e(TAG, s"init_socket() - Unable to resolve host: ${e.getMessage} (Attempt $attempts)")
		  case e: Exception =>
			// Other connection errors
			Log.e(TAG, s"init_socket() - Error establishing connection: ${e.getMessage}")
		}

		// Retry connection after a delay
		if (running) {
		  try {
			Log.d(TAG, "init_socket() - Retrying connection...")
			Thread.sleep(5000)  // Wait for 5 seconds before retrying
		  } catch {
			case ie: InterruptedException => Log.e(TAG, "init_socket() - Interrupted while sleeping", ie)
		  }
		}
	  }
	}

	// Parse host and port from the hostport string
	def parseHostPort(hostport: String): (String, Int) = {
	  val parts = hostport.split(":")
	  if (parts.length == 2) {
		(parts(0), parts(1).toInt)
	  } else {
		(hostport, 14580)  // default port
	  }
	}


    // Send login information to the APRS-IS server
    def sendLogin(): Unit = {
      val callsign = prefs.getCallSsid()
      val passcode = prefs.getPasscode()  // Retrieve passcode from preferences
      val version = "APRSdroid iGate"     // Version information (as in Python example)

      // Format the login message as per the Python example
      val loginMessage = s"user $callsign pass $passcode vers $version\r\n"
      val filterMessage = s"#filter ${prefs.getString("tcp.filter", "")}\r\n"  // Retrieve filter from preferences

      Log.d(TAG, s"sendLogin() - Sending login: $loginMessage")
      Log.d(TAG, s"sendLogin() - Sending filter: $filterMessage")

      // Send the login message to the server
      printWriter.println(loginMessage)
      printWriter.flush()
      Log.d(TAG, "sendLogin() - Login sent.")

      // Send the filter command
      printWriter.println(filterMessage)
      printWriter.flush()
      Log.d(TAG, "sendLogin() - Filter sent.")

      // Read server's response
      val response = bufferedReader.readLine()
      if (response != null) {
        Log.d(TAG, s"sendLogin() - Server response: $response")

        // Handle server response to determine success or failure
        if (response.contains("USER") || response.contains("OK")) {
          Log.d(TAG, "sendLogin() - Login successful!")
        } else if (response.contains("FAIL") || response.contains("ERROR")) {
          Log.d(TAG, s"sendLogin() - Login failed: $response")
          running = false
        } else {
          Log.d(TAG, s"sendLogin() - Unexpected server response: $response")
        }
      } else {
        Log.d(TAG, "sendLogin() - No response received from server")
        running = false
      }
    }

    // Send data (APRS packet) to the APRS-IS server
    def sendData(data: String): Unit = {
      if (socket != null && socket.isConnected && printWriter != null) {
        Log.d(TAG, s"sendData() - Sending data to APRS-IS server: $data")
        try {
          printWriter.println(data)  // Send the data
          printWriter.flush()
        } catch {
          case e: SocketException =>
            Log.e(TAG, s"sendData() - Socket error: ${e.getMessage}")
            reconnect()  // Attempt reconnection if socket error occurs
          case e: IOException =>
            Log.e(TAG, s"sendData() - I/O error: ${e.getMessage}")
            reconnect()  // Attempt reconnection if I/O error occurs
          case e: Exception =>
            Log.e(TAG, s"sendData() - Unexpected error: ${e.getMessage}")
            reconnect()  // Attempt reconnection for any other errors
        }
      } else {
        Log.d(TAG, "sendData() - Connection not available or not connected.")
        // Attempt reconnection if socket is not connected
        reconnect()
      }
    }

    // Send keep-alive packet to the APRS-IS server
    def sendKeepAlive(): Unit = {
      if (socket != null && socket.isConnected && printWriter != null) {
        Log.d(TAG, "sendKeepAlive() - Sending keep-alive packet: #\r\n")
        try {
          printWriter.println("#\r\n")
          printWriter.flush()
        } catch {
          case e: SocketException =>
            Log.e(TAG, s"sendKeepAlive() - Socket error: ${e.getMessage}")
            reconnect()  // Attempt reconnection if socket error occurs
          case e: IOException =>
            Log.e(TAG, s"sendKeepAlive() - I/O error: ${e.getMessage}")
            reconnect()  // Attempt reconnection if I/O error occurs
          case e: Exception =>
            Log.e(TAG, s"sendKeepAlive() - Unexpected error: ${e.getMessage}")
            reconnect()  // Attempt reconnection for any other errors
        }
      } else {
        Log.d(TAG, "sendKeepAlive() - Connection not available or not connected.")
        // Attempt reconnection if socket is not connected
        reconnect()
      }
    }

    // Attempt to detect disconnection by reading from the socket
    def checkSocketConnection(): Boolean = {
      try {
        // Attempt to read a small byte or line (this is a lightweight check)
        val response = bufferedReader.readLine()
        if (response == null) {
          Log.e(TAG, "checkSocketConnection() - No response, socket might be disconnected.")
          return false
        }
        // If we get a response, the connection is still alive
        return true
      } catch {
        case e: SocketException =>
          Log.e(TAG, s"checkSocketConnection() - Socket exception: ${e.getMessage}")
          return false
        case e: IOException =>
          Log.e(TAG, s"checkSocketConnection() - I/O exception: ${e.getMessage}")
          return false
        case e: Exception =>
          Log.e(TAG, s"checkSocketConnection() - Unexpected exception: ${e.getMessage}")
          return false
      }
    }

	// Attempt to detect disconnection and reconnect
	def reconnect(): Unit = {
	  Log.d(TAG, "reconnect() - Attempting to reconnect...")

	  // Properly shutdown the current connection
	  shutdown()

	  // Wait for a short period before trying to reconnect
	  try {
		Thread.sleep(5000)  // Wait for 5 seconds before reconnecting
	  } catch {
		case e: InterruptedException => 
		  Log.e(TAG, "reconnect() - Interrupted while sleeping. Exiting reconnect attempt.")
		  return  // Exit if interrupted during sleep
	  }

	  // Reinitialize the socket and restart the connection
	  init_socket()  // Reinitialize the socket
	  Log.d(TAG, "reconnect() - Reconnection attempt finished")
	}

	override def run(): Unit = {
	  try {
		Log.d(TAG, "run() - Starting connection thread")
		init_socket()

		while (running) {
		  // Check if the thread has been interrupted
		  if (Thread.interrupted()) {
			Log.d(TAG, "run() - Thread interrupted, shutting down gracefully.")
			running = false
		  }

		  if (!checkSocketConnection()) {
			Log.d(TAG, "run() - Connection lost. Attempting to reconnect...")
			reconnect()  // Reconnect if the connection is lost
		  }

		  try {
			// Attempt to read data, this might throw an exception if the socket is closed
			val receivedData = bufferedReader.readLine()
			if (receivedData != null) {
			  Log.d(TAG, s"run() - Received data from server: $receivedData")
			} else {
			  Log.e(TAG, "run() - No data received, server might have closed the connection")
			  running = false  // Stop the thread if no data is received
			}
		  } catch {
			case e: IOException =>
			  Log.e(TAG, s"run() - Error reading from server: ${e.getMessage}", e)
			  running = false  // Stop the thread if there's a read error
			case e: SocketException =>
			  Log.e(TAG, s"run() - SocketException: ${e.getMessage}", e)
			  shutdown()  // Clean up the connection
			  reconnect()  // Reattempt connection			  
			case e: InterruptedException =>
			  Log.d(TAG, "run() - Thread was interrupted during reading/sleep.")
			  running = false  // Stop the thread if it's interrupted
			case e: Exception =>
			  Log.e(TAG, s"run() - Unexpected error: ${e.getMessage}", e)
			  running = false
		  }

		  if (running) {
			// Sleep for a brief period before the next iteration
			try {
			  Thread.sleep(10000)  // Sleep for 10 seconds before sending keep-alive packet
			} catch {
			  case e: InterruptedException =>
				Log.d(TAG, "run() - Thread was interrupted during sleep.")
				running = false  // Stop the thread if it's interrupted during sleep
			}

			// Send the keep-alive packet
			sendKeepAlive()
		  }
		}
	  } catch {
		case e: Exception =>
		  Log.e(TAG, s"run() - Error: ${e.getMessage}", e)
		  running = false
	  } finally {
		shutdown()
	  }
	}

    // Shutdown the connection
    def shutdown(): Unit = {
      Log.d(TAG, "shutdown() - Shutting down connection")
      if (socket != null && !socket.isClosed) {
        try {
          socket.close()
          Log.d(TAG, "shutdown() - Socket closed successfully")
        } catch {
          case e: Exception => Log.e(TAG, s"shutdown() - Error closing socket: ${e.getMessage}")
        }
      }
    }
  }

	def modifyData(data: String): String = {
	  // Find the index of the first colon
	  val colonIndex = data.indexOf(":")

	  if (colonIndex != -1) {
		// Insert ",qAR" before the first colon
		data.substring(0, colonIndex) + ",qAR" + data.substring(colonIndex)
	  } else {
		// If there's no colon, return the data as is (or handle this case as needed)
		data
	  }
	}

	// Modify the data string before passing it to handlePostSubmitData
	def handlePostSubmitData(data: String): Unit = {
	  Log.d(TAG, s"handlePostSubmitData() - Received data: $data")

	  // Modify the data before sending it to the server
	  val modifiedData = modifyData(data)

	  // Log the modified data to confirm the change
	  Log.d(TAG, s"handlePostSubmitData() - Modified data: $modifiedData")

	  // Send the modified data to the APRS-IS server (or other logic as necessary)
	  if (conn != null) {
		conn.sendData(modifiedData)  // Send it to the server
	  } else {
		Log.d(TAG, "handlePostSubmitData() - No active connection to send data.")
	  }
	}
}
