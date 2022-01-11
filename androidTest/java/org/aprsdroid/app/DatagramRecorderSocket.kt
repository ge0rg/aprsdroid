package org.aprsdroid.app

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketAddress

class DatagramRecorderSocket: DatagramSocket() {
    private var remote_addr: InetAddress? = null
    private var remote_port: Int = 0

    override fun bind(addr: SocketAddress?) {
    }

    override fun close() {
    }

    override fun connect(addr: SocketAddress?) {
//        remote_addr = addr
    }

    override fun connect(addr: InetAddress?, port: Int) {
        remote_addr = addr
        remote_port = port
    }

    override fun disconnect() {
    }

    override fun send(p: DatagramPacket?) {
        p?.let {
            Log.d("APRSTest", "Packet to ${it.address.toString()}:${it.port} -> ${it.data.toString()}")
        }
        if(p != null)
            DatagramLog.log.add(p)
    }
}

object DatagramLog {
    val log = arrayListOf<DatagramPacket>()

    fun clear() {
        log.clear()
    }

    fun getLog(): List<DatagramPacket> {
        return log
    }
}
