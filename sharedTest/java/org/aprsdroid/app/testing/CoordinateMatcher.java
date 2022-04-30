package org.aprsdroid.app.testing;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import org.junit.Assert;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CoordinateMatcher {
    private static final String NUMBER = "(-?\\d+(?:\\.\\d*)?)";
    private static final String dms_latitude_pattern = NUMBER + "°\\s*" + NUMBER + "'\\s*" + NUMBER + "\"\\s*([NS])";
    private static final String dms_longitude_pattern = NUMBER + "°\\s*" + NUMBER + "'\\s*" + NUMBER + "\"\\s*([EW])";
    private static final Pattern dms_latitude_regex = Pattern.compile(dms_latitude_pattern, Pattern.CASE_INSENSITIVE);
    private static final Pattern dms_longitude_regex = Pattern.compile(dms_longitude_pattern, Pattern.CASE_INSENSITIVE);

    private static float convertField(CharSequence string, Pattern regex, String name) {
        Matcher matcher = regex.matcher(string);
        Assert.assertTrue(name + " not found", matcher.find());
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

    public static float matchLatitude(CharSequence string) {
        return convertField(string, dms_latitude_regex, "Latitude");
    }

    public static float matchLongitude(CharSequence string) {
        return convertField(string, dms_longitude_regex, "Longitude");
    }
}
