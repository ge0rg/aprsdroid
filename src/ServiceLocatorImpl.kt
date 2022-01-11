package org.aprsdroid.app

import java.net.DatagramSocket

class ServiceLocatorImpl(): ServiceLocator {
    override fun provideDatagramSocket(): DatagramSocket {
        return DatagramSocket()
    }
}
