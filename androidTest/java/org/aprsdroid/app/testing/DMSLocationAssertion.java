package org.aprsdroid.app.testing;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.instanceOf;

import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.test.espresso.NoMatchingViewException;
import androidx.test.espresso.ViewAssertion;

import org.junit.Assert;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DMSLocationAssertion implements ViewAssertion {
    private static final String NUMBER = "(-?\\d+(?:\\.\\d*)?)";
    private static final String dms_latitude_pattern = NUMBER + "°\\s*" + NUMBER + "'\\s*" + NUMBER + "\"\\s*([NS])";
    private static final String dms_longitude_pattern = NUMBER + "°\\s*" + NUMBER + "'\\s*" + NUMBER + "\"\\s*([EW])";
    private static final Pattern dms_latitude_regex = Pattern.compile(dms_latitude_pattern, Pattern.CASE_INSENSITIVE);
    private static final Pattern dms_longitude_regex = Pattern.compile(dms_longitude_pattern, Pattern.CASE_INSENSITIVE);

    private static float convertField(Matcher matcher, String name) {
        float value = 0;
        try {
            int degrees = Integer.parseInt(Objects.requireNonNull(matcher.group(1)));
            Assert.assertThat(name + " degrees", degrees, greaterThanOrEqualTo(0));
            value = (float) degrees;
        } catch (NumberFormatException ex) {
            Assert.fail(name + " degree field not an integer");
        }
        try {
            int minutes = Integer.parseInt(Objects.requireNonNull(matcher.group(2)));
            Assert.assertThat(name + " minutes", minutes, greaterThanOrEqualTo(0));
            value += (float) minutes / 60.0f;
        } catch (NumberFormatException ex) {
            Assert.fail(name + " minute field not an integer");
        }
        try {
            float seconds = Float.parseFloat(Objects.requireNonNull(matcher.group(3)));
            Assert.assertThat(name + " seconds", seconds, greaterThanOrEqualTo(0.0f));
            value += seconds / 3600.0f;
        } catch (NumberFormatException ex) {
            Assert.fail(name + " seconds field not an number");
        }
        String direction = Objects.requireNonNull(matcher.group(4));
        if (direction.equalsIgnoreCase("S") || direction.equalsIgnoreCase("W")) {
            value *= -1.0f;
        }
        return value;
    }

    protected void checkCoordinates(float latitude, float longitude) {
    }

    @Override
    public void check(View view, NoMatchingViewException noViewFoundException) {
        if (view == null)
            throw noViewFoundException;
        Assert.assertThat(view, instanceOf(TextView.class));
        TextView text = (TextView) view;
        Matcher latitude_match = dms_latitude_regex.matcher(text.getText());
        Matcher longitude_match = dms_longitude_regex.matcher(text.getText());
        Assert.assertTrue("Latitude not found", latitude_match.find());
        Assert.assertTrue("Longitude not found", longitude_match.find());
        Log.d("TAG-Map", "StrLat: " + latitude_match.group(0));
        Log.d("TAG-Map", "StrLon: " + longitude_match.group(0));
        float latitude = convertField(latitude_match, "Latitude");
        float longitude = convertField(longitude_match, "Longitude");
        checkCoordinates(latitude, longitude);
    }
}
