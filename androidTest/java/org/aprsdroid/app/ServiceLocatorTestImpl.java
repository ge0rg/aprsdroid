package org.aprsdroid.app;

import java.net.DatagramSocket;
import java.net.SocketException;

class ServiceLocatorTestImpl implements ServiceLocator {
    @Override
    public DatagramSocket provideDatagramSocket() {
        try {
            return new DatagramRecorderSocket();
        } catch (SocketException ex) {
            return null;
        }
    }
}