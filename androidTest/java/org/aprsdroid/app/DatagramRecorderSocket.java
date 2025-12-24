package org.aprsdroid.app;

import android.util.Log;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
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
            Log.d("APRSdroid-test", "Packet to " + p.getAddress().toString() + ":" + p.getPort() + " -> " + new String(p.getData(), StandardCharsets.UTF_8));
            DatagramLog.addPacket(p);
        }
    }

    static class DatagramLog {
        private static ArrayList<DatagramPacket> log = new ArrayList<>();

        static void clear() {
            log.clear();
        }

        static void addPacket(DatagramPacket packet) {
            log.add(packet);
        }

        static List<DatagramPacket> getLog() {
            return log;
        }
    }
}
