package org.aprsdroid.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.test.espresso.action.GeneralLocation;
import androidx.test.espresso.action.GeneralSwipeAction;
import androidx.test.espresso.action.Press;
import androidx.test.espresso.action.Swipe;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import org.aprsdroid.app.testing.DMSLocationAssertion;
import org.aprsdroid.app.testing.SharedPreferencesRule;
import org.aprsdroid.app.testing.SpecificDMSLocationAssertion;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

@RunWith(Enclosed.class)
public class MapModeTest {
    private static final String TAG = "APRSdroid-MapModeTest";
    private static final Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

    private static final ActivityScenarioRule<GoogleMapAct> activityRule =
            new ActivityScenarioRule<>(new Intent(appContext, GoogleMapAct.class)
                    .putExtra("info", R.string.p_source_from_map_save));

    @RunWith(AndroidJUnit4.class)
    public static class GivenDefaultHomeLocation {
        private final SharedPreferencesRule prefsRule = new SharedPreferencesRule() {
            @Override
            protected void modifyPreferences(SharedPreferences preferences) {
                preferences.edit().clear().commit();
            }
        };

        @Rule
        public final RuleChain rules = RuleChain.outerRule(prefsRule).around(activityRule);

        @Test
        public void whenFirstLoaded_thenSaveDisabled() {
            onView(withId(R.id.info))
                    .check(matches(withText("")));
            onView(withId(R.id.accept))
                    .check(matches(not(isEnabled())));
        }

        @Test
        public void whenMapIsDragged_thenPositionAndButtonShown() {
            Assume.assumeThat("Google Play Services requires",
                    GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext),
                    equalTo(ConnectionResult.SUCCESS));
            onView(withId(R.id.mapview))
                    .perform(new GeneralSwipeAction(Swipe.SLOW, GeneralLocation.CENTER,
                            GeneralLocation.CENTER_LEFT, Press.THUMB));
            onView(withId(R.id.info))
                    .check(matches(not(withText(""))));
            onView(withId(R.id.accept))
                    .check(matches(isEnabled()));
        }

        @Test
        public void whenMapIsDragged_thenPositionIsValidCoordinates() {
            Assume.assumeThat("Google Play Services requires",
                    GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext),
                    equalTo(ConnectionResult.SUCCESS));
            onView(withId(R.id.mapview))
                    .perform(new GeneralSwipeAction(Swipe.SLOW, GeneralLocation.CENTER,
                            GeneralLocation.CENTER_LEFT, Press.THUMB));
            onView(withId(R.id.info))
                    .check(new DMSLocationAssertion());
        }
    }

    @RunWith(AndroidJUnit4.class)
    public static class GivenSavedLocationInNEHemisphere {
        private static final float expectedLatitude = 37.50123f;
        private static final float expectedLongitude = 88.25034f;
        private static final float expectedZoom = 4.0f;

        private final SharedPreferencesRule prefsRule = new SharedPreferencesRule() {
            @Override
            protected void modifyPreferences(SharedPreferences preferences) {
                preferences.edit()
                        .putFloat("map_lat", expectedLatitude)
                        .putFloat("map_lon", expectedLongitude)
                        .putFloat("map_zoom", expectedZoom)
                        .commit();
            }
        };

        @Rule
        public final RuleChain rules = RuleChain.outerRule(prefsRule).around(activityRule);

        @Test
        public void whenFirstLoaded_thenSaveDisabled() {
            onView(withId(R.id.info))
                    .check(matches(withText("")));
            onView(withId(R.id.accept))
                    .check(matches(not(isEnabled())));
        }

        @Test
        public void whenMapIsDragged_thenPositionAndButtonShown() {
            Assume.assumeThat("Google Play Services requires",
                    GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext),
                    equalTo(ConnectionResult.SUCCESS));
            onView(withId(R.id.mapview))
                    .perform(new GeneralSwipeAction(Swipe.SLOW, GeneralLocation.CENTER,
                            GeneralLocation.CENTER_LEFT, Press.THUMB));
            onView(withId(R.id.info))
                    .check(matches(not(withText(""))));
            onView(withId(R.id.accept))
                    .check(matches(isEnabled()));
        }

        @Test
        public void whenMapIsDraggedBackAndForth_thenPositionIsOriginalCoordinates() {
            Assume.assumeThat("Google Play Services requires",
                    GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext),
                    equalTo(ConnectionResult.SUCCESS));
            onView(withId(R.id.mapview))
                    .perform(new GeneralSwipeAction(Swipe.SLOW, GeneralLocation.CENTER,
                            GeneralLocation.CENTER_LEFT, Press.THUMB));
            onView(withId(R.id.mapview))
                    .perform(new GeneralSwipeAction(Swipe.SLOW, GeneralLocation.CENTER_LEFT,
                            GeneralLocation.CENTER, Press.THUMB));
            onView(withId(R.id.info))
                    .check(new SpecificDMSLocationAssertion(expectedLatitude, expectedLongitude));
        }

        @Test
        public void whenMapIsDraggedBackAndForthAndSaved_thenPositionIsSavedCorrectly() {
            Assume.assumeThat("Google Play Services requires",
                    GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext),
                    equalTo(ConnectionResult.SUCCESS));
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                Log.w(TAG, "Sleep was interrupted: " + ex);
            }
            prefsRule.getPreferences()
                    .edit()
                    .remove("map_lat")
                    .remove("map_lon")
                    .remove("map_zoom")
                    .commit();
            onView(withId(R.id.mapview))
                    .perform(new GeneralSwipeAction(Swipe.SLOW, GeneralLocation.CENTER,
                            GeneralLocation.CENTER_LEFT, Press.THUMB));
            onView(withId(R.id.mapview))
                    .perform(new GeneralSwipeAction(Swipe.SLOW, GeneralLocation.CENTER_LEFT,
                            GeneralLocation.CENTER, Press.THUMB));
            onView(withId(R.id.accept))
                    .perform(click());
            float actualLatitude = prefsRule.getPreferences().getFloat("map_lat", 0.0f);
            float actualLongitude = prefsRule.getPreferences().getFloat("map_lon", 0.0f);
            float actualZoom = prefsRule.getPreferences().getFloat("map_zoom", 0.0f);
            assertThat("Latitude", (double) actualLatitude, closeTo(expectedLatitude, 5e-2));
            assertThat("Longitude", (double) actualLongitude, closeTo(expectedLongitude, 5e-2));
            assertThat("Zoom", (double) actualZoom, closeTo(expectedZoom, 1e-7));
        }
    }

    @RunWith(AndroidJUnit4.class)
    public static class GivenSavedLocationInSEHemisphere {
        private static final float expectedLatitude = -37.50123f;
        private static final float expectedLongitude = 88.25034f;
        private static final float expectedZoom = 4.5f;

        private final SharedPreferencesRule prefsRule = new SharedPreferencesRule() {
            @Override
            protected void modifyPreferences(SharedPreferences preferences) {
                preferences.edit()
                        .putFloat("map_lat", expectedLatitude)
                        .putFloat("map_lon", expectedLongitude)
                        .putFloat("map_zoom", expectedZoom)
                        .commit();
            }
        };

        @Rule
        public final RuleChain rules = RuleChain.outerRule(prefsRule).around(activityRule);

        @Test
        public void whenFirstLoaded_thenSaveDisabled() {
            onView(withId(R.id.info))
                    .check(matches(withText("")));
            onView(withId(R.id.accept))
                    .check(matches(not(isEnabled())));
        }

        @Test
        public void whenMapIsDragged_thenPositionAndButtonShown() {
            Assume.assumeThat("Google Play Services requires",
                    GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext),
                    equalTo(ConnectionResult.SUCCESS));
            onView(withId(R.id.mapview))
                    .perform(new GeneralSwipeAction(Swipe.SLOW, GeneralLocation.CENTER,
                            GeneralLocation.CENTER_LEFT, Press.THUMB));
            onView(withId(R.id.info))
                    .check(matches(not(withText(""))));
            onView(withId(R.id.accept))
                    .check(matches(isEnabled()));
        }

        @Test
        public void whenMapIsDraggedBackAndForth_thenPositionIsOriginalCoordinates() {
            Assume.assumeThat("Google Play Services requires",
                    GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext),
                    equalTo(ConnectionResult.SUCCESS));
            onView(withId(R.id.mapview))
                    .perform(new GeneralSwipeAction(Swipe.SLOW, GeneralLocation.CENTER,
                            GeneralLocation.CENTER_LEFT, Press.THUMB));
            onView(withId(R.id.mapview))
                    .perform(new GeneralSwipeAction(Swipe.SLOW, GeneralLocation.CENTER_LEFT,
                            GeneralLocation.CENTER, Press.THUMB));
            onView(withId(R.id.info))
                    .check(new SpecificDMSLocationAssertion(expectedLatitude, expectedLongitude));
        }

        @Test
        public void whenMapIsDraggedBackAndForthAndSaved_thenPositionIsSavedCorrectly() {
            Assume.assumeThat("Google Play Services requires",
                    GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext),
                    equalTo(ConnectionResult.SUCCESS));
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                Log.w(TAG, "Sleep was interrupted: " + ex);
            }
            prefsRule.getPreferences()
                    .edit()
                    .remove("map_lat")
                    .remove("map_lon")
                    .remove("map_zoom")
                    .commit();
            onView(withId(R.id.mapview))
                    .perform(new GeneralSwipeAction(Swipe.SLOW, GeneralLocation.CENTER,
                            GeneralLocation.CENTER_LEFT, Press.THUMB));
            onView(withId(R.id.mapview))
                    .perform(new GeneralSwipeAction(Swipe.SLOW, GeneralLocation.CENTER_LEFT,
                            GeneralLocation.CENTER, Press.THUMB));
            onView(withId(R.id.accept))
                    .perform(click());
            float actualLatitude = prefsRule.getPreferences().getFloat("map_lat", 0.0f);
            float actualLongitude = prefsRule.getPreferences().getFloat("map_lon", 0.0f);
            float actualZoom = prefsRule.getPreferences().getFloat("map_zoom", 0.0f);
            assertThat("Latitude", (double) actualLatitude, closeTo(expectedLatitude, 5e-2));
            assertThat("Longitude", (double) actualLongitude, closeTo(expectedLongitude, 5e-2));
            assertThat("Zoom", (double) actualZoom, closeTo(expectedZoom, 1e-7));
        }
    }

    @RunWith(AndroidJUnit4.class)
    public static class GivenSavedLocationInNWHemisphere {
        private static final float expectedLatitude = 37.50123f;
        private static final float expectedLongitude = -88.25034f;
        private static final float expectedZoom = 5.0f;

        private final SharedPreferencesRule prefsRule = new SharedPreferencesRule() {
            @Override
            protected void modifyPreferences(SharedPreferences preferences) {
                preferences.edit()
                        .putFloat("map_lat", expectedLatitude)
                        .putFloat("map_lon", expectedLongitude)
                        .putFloat("map_zoom", expectedZoom)
                        .commit();
            }
        };

        @Rule
        public final RuleChain rules = RuleChain.outerRule(prefsRule).around(activityRule);

        @Test
        public void whenFirstLoaded_thenSaveDisabled() {
            onView(withId(R.id.info))
                    .check(matches(withText("")));
            onView(withId(R.id.accept))
                    .check(matches(not(isEnabled())));
        }

        @Test
        public void whenMapIsDragged_thenPositionAndButtonShown() {
            Assume.assumeThat("Google Play Services requires",
                    GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext),
                    equalTo(ConnectionResult.SUCCESS));
            onView(withId(R.id.mapview))
                    .perform(new GeneralSwipeAction(Swipe.SLOW, GeneralLocation.CENTER,
                            GeneralLocation.CENTER_LEFT, Press.THUMB));
            onView(withId(R.id.info))
                    .check(matches(not(withText(""))));
            onView(withId(R.id.accept))
                    .check(matches(isEnabled()));
        }

        @Test
        public void whenMapIsDraggedBackAndForth_thenPositionIsOriginalCoordinates() {
            Assume.assumeThat("Google Play Services requires",
                    GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext),
                    equalTo(ConnectionResult.SUCCESS));
            onView(withId(R.id.mapview))
                    .perform(new GeneralSwipeAction(Swipe.SLOW, GeneralLocation.CENTER,
                            GeneralLocation.CENTER_LEFT, Press.THUMB));
            onView(withId(R.id.mapview))
                    .perform(new GeneralSwipeAction(Swipe.SLOW, GeneralLocation.CENTER_LEFT,
                            GeneralLocation.CENTER, Press.THUMB));
            onView(withId(R.id.info))
                    .check(new SpecificDMSLocationAssertion(expectedLatitude, expectedLongitude));
        }

        @Test
        public void whenMapIsDraggedBackAndForthAndSaved_thenPositionIsSavedCorrectly() {
            Assume.assumeThat("Google Play Services requires",
                    GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext),
                    equalTo(ConnectionResult.SUCCESS));
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                Log.w(TAG, "Sleep was interrupted: " + ex);
            }
            prefsRule.getPreferences()
                    .edit()
                    .remove("map_lat")
                    .remove("map_lon")
                    .remove("map_zoom")
                    .commit();
            onView(withId(R.id.mapview))
                    .perform(new GeneralSwipeAction(Swipe.SLOW, GeneralLocation.CENTER,
                            GeneralLocation.CENTER_LEFT, Press.THUMB));
            onView(withId(R.id.mapview))
                    .perform(new GeneralSwipeAction(Swipe.SLOW, GeneralLocation.CENTER_LEFT,
                            GeneralLocation.CENTER, Press.THUMB));
            onView(withId(R.id.accept))
                    .perform(click());
            float actualLatitude = prefsRule.getPreferences().getFloat("map_lat", 0.0f);
            float actualLongitude = prefsRule.getPreferences().getFloat("map_lon", 0.0f);
            float actualZoom = prefsRule.getPreferences().getFloat("map_zoom", 0.0f);
            assertThat("Latitude", (double) actualLatitude, closeTo(expectedLatitude, 5e-2));
            assertThat("Longitude", (double) actualLongitude, closeTo(expectedLongitude, 5e-2));
            assertThat("Zoom", (double) actualZoom, closeTo(expectedZoom, 1e-7));
        }
    }

    @RunWith(AndroidJUnit4.class)
    public static class GivenSavedLocationInSWHemisphere {
        private static final float expectedLatitude = -37.50123f;
        private static final float expectedLongitude = -88.25034f;
        private static final float expectedZoom = 5.5f;

        private final SharedPreferencesRule prefsRule = new SharedPreferencesRule() {
            @Override
            protected void modifyPreferences(SharedPreferences preferences) {
                preferences.edit()
                        .putFloat("map_lat", expectedLatitude)
                        .putFloat("map_lon", expectedLongitude)
                        .putFloat("map_zoom", expectedZoom)
                        .commit();
            }
        };

        @Rule
        public final RuleChain rules = RuleChain.outerRule(prefsRule).around(activityRule);

        @Test
        public void whenFirstLoaded_thenSaveDisabled() {
            onView(withId(R.id.info))
                    .check(matches(withText("")));
            onView(withId(R.id.accept))
                    .check(matches(not(isEnabled())));
        }

        @Test
        public void whenMapIsDragged_thenPositionAndButtonShown() {
            Assume.assumeThat("Google Play Services requires",
                    GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext),
                    equalTo(ConnectionResult.SUCCESS));
            onView(withId(R.id.mapview))
                    .perform(new GeneralSwipeAction(Swipe.SLOW, GeneralLocation.CENTER,
                            GeneralLocation.CENTER_LEFT, Press.THUMB));
            onView(withId(R.id.info))
                    .check(matches(not(withText(""))));
            onView(withId(R.id.accept))
                    .check(matches(isEnabled()));
        }

        @Test
        public void whenMapIsDraggedBackAndForth_thenPositionIsOriginalCoordinates() {
            Assume.assumeThat("Google Play Services requires",
                    GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext),
                    equalTo(ConnectionResult.SUCCESS));
            onView(withId(R.id.mapview))
                    .perform(new GeneralSwipeAction(Swipe.SLOW, GeneralLocation.CENTER,
                            GeneralLocation.CENTER_LEFT, Press.THUMB));
            onView(withId(R.id.mapview))
                    .perform(new GeneralSwipeAction(Swipe.SLOW, GeneralLocation.CENTER_LEFT,
                            GeneralLocation.CENTER, Press.THUMB));
            onView(withId(R.id.info))
                    .check(new SpecificDMSLocationAssertion(expectedLatitude, expectedLongitude));
        }

        @Test
        public void whenMapIsDraggedBackAndForthAndSaved_thenPositionIsSavedCorrectly() {
            Assume.assumeThat("Google Play Services requires",
                    GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext),
                    equalTo(ConnectionResult.SUCCESS));
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                Log.w(TAG, "Sleep was interrupted: " + ex);
            }
            prefsRule.getPreferences()
                    .edit()
                    .remove("map_lat")
                    .remove("map_lon")
                    .remove("map_zoom")
                    .commit();
            onView(withId(R.id.mapview))
                    .perform(new GeneralSwipeAction(Swipe.SLOW, GeneralLocation.CENTER,
                            GeneralLocation.CENTER_LEFT, Press.THUMB));
            onView(withId(R.id.mapview))
                    .perform(new GeneralSwipeAction(Swipe.SLOW, GeneralLocation.CENTER_LEFT,
                            GeneralLocation.CENTER, Press.THUMB));
            onView(withId(R.id.accept))
                    .perform(click());
            float actualLatitude = prefsRule.getPreferences().getFloat("map_lat", 0.0f);
            float actualLongitude = prefsRule.getPreferences().getFloat("map_lon", 0.0f);
            float actualZoom = prefsRule.getPreferences().getFloat("map_zoom", 0.0f);
            assertThat("Latitude", (double) actualLatitude, closeTo(expectedLatitude, 5e-2));
            assertThat("Longitude", (double) actualLongitude, closeTo(expectedLongitude, 5e-2));
            assertThat("Zoom", (double) actualZoom, closeTo(expectedZoom, 1e-7));
        }
    }
}
