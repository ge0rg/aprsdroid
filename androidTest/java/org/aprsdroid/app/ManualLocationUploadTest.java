package org.aprsdroid.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ManualLocationUploadTest {
    private Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
    @Test
    public void testSingleShotUpload() {
        APRSdroidApplication androidApp = (APRSdroidApplication)appContext.getApplicationContext();
        androidApp.setServiceLocator(new ServiceLocatorTestImpl());

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("callsign", "X1ABC");
        editor.putString("passcode", "12345");  // Invalid passcode so it can't accidentally submit data
        editor.putInt("ssid", 2);
        editor.putString("loc_source", "manual");
        editor.putFloat("manual_lat", 45.0f);
        editor.putFloat("manual_lon", 123.0f);
        editor.putBoolean("periodicposition", false);
        editor.putInt("interval", 1);
        editor.putString("backendname", "aprsis-tcpip-udp");
        editor.commit();

        appContext.startService(AprsService.intent(appContext, AprsService.SERVICE_ONCE()));

        try {
            Thread.sleep(3000);
        } catch (InterruptedException ex) {
        }
    }
}
