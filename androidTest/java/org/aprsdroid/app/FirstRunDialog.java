package org.aprsdroid.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withHint;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.StringContains.containsString;

import android.content.SharedPreferences;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.aprsdroid.app.testing.SharedPreferencesRule;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FirstRunDialog {
    private final String pref_callsign = "callsign";
    private final String pref_passcode = "passcode";
    public ActivityScenarioRule activityRule = new ActivityScenarioRule<>(LogActivity.class);
    public SharedPreferencesRule prefsRule = new SharedPreferencesRule() {
        @Override
        protected void modifyPreferences(SharedPreferences preferences) {
            preferences.edit().clear().commit();
        }
    };
    @Rule
    public RuleChain rules = RuleChain.outerRule(prefsRule).around(activityRule);

    @Test
    public void givenAFirstTimeRun_whenProvidedABadPasscode_ThenDialogStaysOpen() {
        onView(isRoot())
                .inRoot(isDialog())
                .check(matches(allOf(
                        hasDescendant(withText(containsString("Welcome to APRSdroid"))),
                        hasDescendant(withId(R.id.callsign)),
                        hasDescendant(withId(R.id.passcode)))));
        onView(withId(R.id.callsign))
                .check(matches(withHint(containsString("Callsign"))))
                .perform(typeText("XA1AAA"), closeSoftKeyboard());
        onView(withId(R.id.passcode))
                .check(matches(withHint(containsString("Passcode"))))
                .perform(typeText("12345"), closeSoftKeyboard());
        onView(withId(android.R.id.button1)).perform(click());  // OK Button
        onView(isRoot())
                .inRoot(isDialog())
                .check(matches(allOf(
                        hasDescendant(withText(containsString("Welcome to APRSdroid"))),
                        hasDescendant(withId(R.id.callsign)),
                        hasDescendant(withId(R.id.passcode)))));
        try {
            Thread.sleep(5000);
        } catch (InterruptedException ex) {
        }
        Assert.assertTrue(true);
    }

    @Test
    public void givenAFirstTimeRun_whenProvidedAGoodPasscode_ThenDialogCloses() {
        onView(isRoot())
                .inRoot(isDialog())
                .check(matches(allOf(
                        hasDescendant(withText(containsString("Welcome to APRSdroid"))),
                        hasDescendant(withId(R.id.callsign)),
                        hasDescendant(withId(R.id.passcode)))));
        onView(withId(R.id.callsign))
                .check(matches(withHint(containsString("Callsign"))))
                .perform(typeText("XA1AAA"), closeSoftKeyboard());
        onView(withId(R.id.passcode))
                .check(matches(withHint(containsString("Passcode"))))
                .perform(typeText("23459"), closeSoftKeyboard());
        onView(withId(android.R.id.button1)).perform(click());  // OK Button
        onView(allOf(
                isRoot(),
                hasDescendant(withText(containsString("Welcome to APRSdroid"))),
                hasDescendant(withId(R.id.callsign)),
                hasDescendant(withId(R.id.passcode))))
                .check(doesNotExist());
    }

    @Test
    public void givenAFirstTimeRun_whenProvidedAGoodPasscode_ThenPrefsSaved() {
        String expected_callsign = "XA1AAA";
        String expected_passcode = "23459";
        SharedPreferences prefs = prefsRule.getPreferences();
        Assert.assertNull("Callsign", prefs.getString(pref_callsign, null));
        Assert.assertNull("Passcode", prefs.getString(pref_passcode, null));
        onView(withId(R.id.callsign))
                .check(matches(withHint(containsString("Callsign"))))
                .perform(typeText(expected_callsign), closeSoftKeyboard());
        onView(withId(R.id.passcode))
                .check(matches(withHint(containsString("Passcode"))))
                .perform(typeText(expected_passcode), closeSoftKeyboard());
        onView(withId(android.R.id.button1)).perform(click());  // OK Button
        Assert.assertEquals("Callsign", expected_callsign, prefs.getString(pref_callsign, null));
        Assert.assertEquals("Passcode", expected_passcode, prefs.getString(pref_passcode, null));
    }
}
