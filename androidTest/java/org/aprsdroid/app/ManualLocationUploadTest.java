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
//        androidApp.setServiceLocator(new ServiceLocatorTestImpl());
        APRSdroidApplication$.MODULE$.setServiceLocator((org.aprsdroid.app.ServiceLocator) new ServiceLocatorTestImpl());

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("callsign", "X1ABC");
        editor.putString("passcode", "12345");  // Invalid passcode so it can't accidentally submit data
        editor.putString("ssid", "2");
        editor.putString("loc_source", "manual");
        editor.putString("manual_lat", "45.0");
        editor.putString("manual_lon", "123.0");
        editor.putBoolean("periodicposition", false);
        editor.putString("interval", "1");
        editor.putString("backendname", "aprsis-tcpip-udp");
        editor.commit();

        appContext.startService(AprsService$.MODULE$.intent(appContext, AprsService$.MODULE$.SERVICE_ONCE()));

        try {
            Thread.sleep(30000);
        } catch (InterruptedException ex) {
        }
    }
}
