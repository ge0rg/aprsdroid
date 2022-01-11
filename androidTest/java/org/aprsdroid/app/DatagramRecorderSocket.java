package org.aprsdroid.app;

import android.util.Log;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

class DatagramRecorderSocket extends DatagramSocket {
    private InetAddress remote_addr = null;
    private int remote_port = 0;

    public DatagramRecorderSocket() throws SocketException {
    }

    @Override
    public void bind(SocketAddress addr) {
    }

    @Override
    public void close() {
    }

    @Override
    public void connect(SocketAddress addr) {
//        remote_addr = addr;
    }

    @Override
    public void connect(InetAddress addr, int port) {
        remote_addr = addr;
        remote_port = port;
    }

    @Override
    public void disconnect() {
    }

    @Override
    public void send(DatagramPacket p) {
        if(p != null) {
            Log.d("APRSTest", "Packet to ${it.address.toString()}:${it.port} -> ${it.data.toString()}");
        }
        if(p != null)
            DatagramLog.log.add(p);
    }

    static class DatagramLog {
        static ArrayList<DatagramPacket> log = new ArrayList<>();

        static void clear() {
            log.clear();
        }

        static List<DatagramPacket> getLog() {
            return log;
        }
    }
}
