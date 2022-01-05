package org.aprsdroid.app.testing;

import static org.hamcrest.Matchers.instanceOf;

import android.view.View;
import android.widget.TextView;

import androidx.test.espresso.NoMatchingViewException;
import androidx.test.espresso.ViewAssertion;

import org.junit.Assert;

public class DMSLocationAssertion implements ViewAssertion {
    protected void checkCoordinates(float latitude, float longitude) {
    }

    @Override
    public void check(View view, NoMatchingViewException noViewFoundException) {
        if (view == null)
            throw noViewFoundException;
        Assert.assertThat(view, instanceOf(TextView.class));
        TextView text = (TextView) view;
        float latitude = CoordinateMatcher.matchLatitude(text.getText());
        float longitude = CoordinateMatcher.matchLongitude(text.getText());
        checkCoordinates(latitude, longitude);
    }
}
