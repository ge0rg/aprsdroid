package org.aprsdroid.app

import java.net.DatagramSocket

class ServiceLocatorTestImpl: ServiceLocator {
    override fun provideDatagramSocket(): DatagramSocket {
        return DatagramRecorderSocket()
    }
}