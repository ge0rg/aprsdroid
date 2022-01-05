package org.aprsdroid.app;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;

import org.aprsdroid.app.testing.CoordinateMatcher;
import org.junit.Test;

public class CoordinateTest {
    // Reference data generated from https://www.pgc.umn.edu/apps/convert/
    private static final String providedNLatitude = "77° 15' 30\" N";
    private static final float expectedNLatitude = 77.258333f;
    private static final String providedELongitude = "164° 45' 15\" E";
    private static final float expectedELongitude = 164.754167f;
    private static final String providedSLatitude = "45° 30' 45\" S";
    private static final float expectedSLatitude = -45.5125f;
    private static final String providedWLongitude = "97° 20' 40\" W";
    private static final float expectedWLongitude = -97.344444f;

    @Test
    public void givenDMSLatitudeInN_whenParsingString_ThenShouldMatchDecimal() {
        float value = CoordinateMatcher.matchLatitude(providedNLatitude);
        assertThat("Latitude", (double) value, closeTo((double) expectedNLatitude, 1e-7));
    }

    @Test
    public void givenDMSLongitudeInE_whenParsingString_ThenShouldMatchDecimal() {
        float value = CoordinateMatcher.matchLongitude(providedELongitude);
        assertThat("Longitude", (double) value, closeTo((double) expectedELongitude, 1e-7));
    }

    @Test
    public void givenDMSLatitudeInS_whenParsingString_ThenShouldMatchDecimal() {
        float value = CoordinateMatcher.matchLatitude(providedSLatitude);
        assertThat("Latitude", (double) value, closeTo((double) expectedSLatitude, 1e-7));
    }

    @Test
    public void givenDMSLongitudeInW_whenParsingString_ThenShouldMatchDecimal() {
        float value = CoordinateMatcher.matchLongitude(providedWLongitude);
        assertThat("Longitude", (double) value, closeTo((double) expectedWLongitude, 1e-7));
    }
}
