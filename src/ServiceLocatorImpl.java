package org.aprsdroid.app;

import java.net.DatagramSocket;
import java.net.SocketException;

class ServiceLocatorImpl implements ServiceLocator {
    public DatagramSocket provideDatagramSocket() {
        try {
            return new DatagramSocket();
        } catch (SocketException ex) {
            return null;
        }
    }
}
