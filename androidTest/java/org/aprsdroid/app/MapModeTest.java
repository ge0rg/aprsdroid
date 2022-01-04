package org.aprsdroid.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.test.espresso.NoMatchingViewException;
import androidx.test.espresso.ViewAssertion;
import androidx.test.espresso.action.GeneralLocation;
import androidx.test.espresso.action.GeneralSwipeAction;
import androidx.test.espresso.action.Press;
import androidx.test.espresso.action.Swipe;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(Enclosed.class)
public class MapModeTest {
    @RunWith(AndroidJUnit4.class)
    public static class Child {
        private final Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        public final SharedPreferencesRule prefsRule = new SharedPreferencesRule() {
            @Override
            protected void modifyPreferences(SharedPreferences preferences) {
                preferences.edit().clear().commit();
            }
        };

        public final ActivityScenarioRule<GoogleMapAct> activityRule =
                new ActivityScenarioRule<>(new Intent(appContext, GoogleMapAct.class)
                        .putExtra("info", R.string.p_source_from_map_save));

        @Rule
        public final RuleChain rules = RuleChain.outerRule(prefsRule).around(activityRule);

        @Test
        public void givenARequestForPosition_whenFirstLoaded_thenSaveDisabled() {
            onView(withId(R.id.info))
                    .check(matches(withText("")));
            onView(withId(R.id.accept))
                    .check(matches(not(isEnabled())));
        }

        @Test
        public void givenARequestForPosition_whenNW_thenPosPos() {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
            Log.d("TAG-Map", "Lat: " + prefs.getFloat("map_lat", 0.0f) + ", Lon: " + prefs.getFloat("map_lon", 0.0f));
            onView(withId(R.id.mapview))
                    .perform(new GeneralSwipeAction(Swipe.SLOW, GeneralLocation.CENTER,
                            GeneralLocation.CENTER_LEFT, Press.THUMB));
            onView(withId(R.id.mapview))
                    .perform(new GeneralSwipeAction(Swipe.SLOW, GeneralLocation.CENTER_LEFT,
                            GeneralLocation.CENTER, Press.THUMB));
            onView(withId(R.id.info))
                    .check(matches(not(withText(""))));
            onView(withId(R.id.accept))
                    .check(matches(isEnabled()));
        }

        @Test
        public void givenARequestForPosition_whenNW_thenPosPos2() {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
            Log.d("TAG-Map", "Lat: " + prefs.getFloat("map_lat", 0.0f) + ", Lon: " + prefs.getFloat("map_lon", 0.0f));
            onView(withId(R.id.mapview))
                    .perform(new GeneralSwipeAction(Swipe.SLOW, GeneralLocation.CENTER,
                            GeneralLocation.CENTER_LEFT, Press.THUMB));
            onView(withId(R.id.mapview))
                    .perform(new GeneralSwipeAction(Swipe.SLOW, GeneralLocation.CENTER_LEFT,
                            GeneralLocation.CENTER, Press.THUMB));
            onView(withId(R.id.info))
                    .check(new DMSLocationAssertion());
        }

        public static class DMSLocationAssertion implements ViewAssertion {
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

        public static class SpecificDMSLocationAssertion extends DMSLocationAssertion {
            private final float expectedLatitude;
            private final float expectedLongitude;

            public SpecificDMSLocationAssertion(float myExpectedLatitude, float myExpectedLongitude) {
                expectedLatitude = myExpectedLatitude;
                expectedLongitude = myExpectedLongitude;
            }

            @Override
            protected void checkCoordinates(float latitude, float longitude) {
                super.checkCoordinates(latitude, longitude);
                Assert.assertThat("Latitude", (double) latitude, closeTo((double) expectedLatitude, 0.000001));
                Assert.assertThat("Longitude", (double) longitude, closeTo((double) expectedLongitude, 0.000001));
            }
        }
    }
}
