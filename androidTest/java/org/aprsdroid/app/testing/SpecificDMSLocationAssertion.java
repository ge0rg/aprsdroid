package org.aprsdroid.app.testing;

import static org.hamcrest.Matchers.closeTo;

import org.junit.Assert;

public class SpecificDMSLocationAssertion extends DMSLocationAssertion {
    private final float expectedLatitude;
    private final float expectedLongitude;

    public SpecificDMSLocationAssertion(float myExpectedLatitude, float myExpectedLongitude) {
        expectedLatitude = myExpectedLatitude;
        expectedLongitude = myExpectedLongitude;
    }

    @Override
    protected void checkCoordinates(float latitude, float longitude) {
        super.checkCoordinates(latitude, longitude);
        Assert.assertThat("Latitude", (double) latitude, closeTo((double) expectedLatitude, 0.05));
        Assert.assertThat("Longitude", (double) longitude, closeTo((double) expectedLongitude, 0.05));
    }
}
