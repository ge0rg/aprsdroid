package org.aprsdroid.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.InstrumentationRegistry;
import android.util.Log;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.DatagramPacket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.ab0oo.aprs.parser.APRSPacket;
import net.ab0oo.aprs.parser.InformationField;
import net.ab0oo.aprs.parser.Parser;
import net.ab0oo.aprs.parser.Position;
import net.ab0oo.aprs.parser.PositionPacket;

@RunWith(AndroidJUnit4.class)
public class ManualLocationUploadTest {
    private Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);

    // Invalid passcode used so it can't accidentally submit data
    private final String expected_callsign = "X1ABC";
    private final String expected_passcode = "12345";
    private final String expected_ssid = "2";
    private final float expected_latitude = 45.0f;
    private final float expected_longitude = 123.0f;

    @Test
    public void testSingleShotUpload() {
        APRSdroidApplication androidApp = (APRSdroidApplication)appContext.getApplicationContext();
        androidApp.setServiceLocator(new ServiceLocatorTestImpl());
        Log.v("APRSdroid-test", "Stopping APRS service");
        appContext.stopService(new Intent(appContext, AprsService.class));
        try {
            // TODO Synchronize with service lifecycle
            Thread.sleep(1*1000);
        } catch (InterruptedException ex) {
            Log.w("APRSdroid-test", "Sleep was interrupted: " + ex.toString());
        }
        DatagramRecorderSocket.DatagramLog.clear();

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("callsign", expected_callsign);
        editor.putString("passcode", expected_passcode);
        editor.putString("ssid", expected_ssid);
        editor.putString("loc_source", "manual");
        editor.putString("manual_lat", String.valueOf(expected_latitude));
        editor.putString("manual_lon", String.valueOf(expected_longitude));
        editor.putBoolean("periodicposition", false);
        editor.putString("interval", "1");
        //editor.putString("backendname", "aprsis-tcpip-udp");
        editor.putString("proto", "aprsis");
        editor.putString("aprsis", "udp");
        editor.commit();

        Log.v("APRSdroid-test", "Starting one-shot APRS service");
        appContext.startService(AprsService$.MODULE$.intent(appContext, AprsService$.MODULE$.SERVICE_ONCE()));

        try {
            // TODO Synchronize with service lifecycle
            Thread.sleep(5*1000);
        } catch (InterruptedException ex) {
            Log.w("APRSdroid-test", "Sleep was interrupted: " + ex.toString());
        }

        List<DatagramPacket> packets = DatagramRecorderSocket.DatagramLog.getLog();
        Assert.assertEquals("Exactly one packet expected", 1, packets.size());
        String sections = new String(packets.get(0).getData(), StandardCharsets.UTF_8);
        String[] components = sections.split("\r\n");
        Assert.assertEquals("Packet should only have 2 sections", 2, components.length);
        String[] header = components[0].split(" ");
        Map<String, String> fields = new HashMap<String, String>();
        for(int i = 0; i < header.length; i += 2) {
            if(i+1 < header.length) {
                fields.put(header[i], header[i+1]);
            }
        }
        String expected_user = String.format("%s-%s", expected_callsign, expected_ssid);
        Assert.assertTrue("user field missing", fields.containsKey("user"));
        Assert.assertTrue("pass field missing", fields.containsKey("user"));
        Assert.assertEquals("User id incorrect", expected_user, fields.get("user"));
        Assert.assertEquals("Passcode incorrect", expected_passcode, fields.get("pass"));
        APRSPacket decodedPacket;
        try {
            decodedPacket = Parser.parse(components[1]);
        } catch(Exception ex) {
            Assert.fail("Exception while parsing APRS packet: " + ex.toString());
            return;
        }
        InformationField info = decodedPacket.getAprsInformation();
        PositionPacket positionPacket;
        try {
            positionPacket = (PositionPacket) info;
        } catch(ClassCastException ex) {
            Assert.fail("Info field does not contain a position packet");
            return;
        }
        Position position = positionPacket.getPosition();
        Assert.assertEquals("latitude", expected_latitude, position.getLatitude(), 1e-7);
        Assert.assertEquals("longitude", expected_longitude, position.getLongitude(), 1e-7);
    }
}
