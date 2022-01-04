package org.aprsdroid.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public abstract class SharedPreferencesRule implements TestRule {
    private SharedPreferences preferences;

    protected abstract void modifyPreferences(SharedPreferences preferences);

    public SharedPreferences getPreferences() {
        return preferences;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        preferences = PreferenceManager.getDefaultSharedPreferences(appContext);
        modifyPreferences(preferences);
        return base;
    }
}
