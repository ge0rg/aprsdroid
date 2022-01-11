package org.aprsdroid.app

import android.app.Instrumentation
import android.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManualLocationUploadTest {
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
    @Test fun testSingleShotUpload() {
        val androidApp = appContext as APRSdroidApplication
        androidApp.setServiceLocator(ServiceLocatorTestImpl())

        prefs.edit().apply() {
            putString("callsign", "X1ABC")
            putString("passcode", "12345")  // Invalid passcode so it can't accidentally submit data
            putInt("ssid", 2)
            putString("loc_source", "manual")
            putFloat("manual_lat", 45.0f)
            putFloat("manual_lon", 123.0f)
            putBoolean("periodicposition", false)
            putInt("interval", 1)
            putString("backendname", "aprsis-tcpip-udp")
        }

        appContext.startService(AprsService.intent(appContext, AprsService.SERVICE_ONCE()))

        Thread.sleep(3000)
    }
}