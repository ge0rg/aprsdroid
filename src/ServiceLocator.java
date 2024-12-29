package org.aprsdroid.app;

import java.net.DatagramSocket;

interface ServiceLocator {
     public DatagramSocket provideDatagramSocket();
}
