package org.aprsdroid.app

import java.net.DatagramSocket

interface ServiceLocator {
     fun provideDatagramSocket(): DatagramSocket
}
