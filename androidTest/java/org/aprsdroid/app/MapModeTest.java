package org.aprsdroid.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.test.espresso.action.GeneralLocation;
import androidx.test.espresso.action.GeneralSwipeAction;
import androidx.test.espresso.action.Press;
import androidx.test.espresso.action.Swipe;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.aprsdroid.app.testing.DMSLocationAssertion;
import org.aprsdroid.app.testing.SharedPreferencesRule;
import org.aprsdroid.app.testing.SpecificDMSLocationAssertion;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

@RunWith(Enclosed.class)
public class MapModeTest {
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
        private static final float expectedLongtude = 88.25034f;

        private final SharedPreferencesRule prefsRule = new SharedPreferencesRule() {
            @Override
            protected void modifyPreferences(SharedPreferences preferences) {
                preferences.edit()
                        .putFloat("map_lat", expectedLatitude)
                        .putFloat("map_lon", expectedLongtude)
                        .putFloat("map_zoom", 5.0f)
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
            onView(withId(R.id.mapview))
                    .perform(new GeneralSwipeAction(Swipe.SLOW, GeneralLocation.CENTER,
                            GeneralLocation.CENTER_LEFT, Press.THUMB));
            onView(withId(R.id.mapview))
                    .perform(new GeneralSwipeAction(Swipe.SLOW, GeneralLocation.CENTER_LEFT,
                            GeneralLocation.CENTER, Press.THUMB));
            onView(withId(R.id.info))
                    .check(new SpecificDMSLocationAssertion(expectedLatitude, expectedLongtude));
        }
    }
}
