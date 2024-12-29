package org.aprsdroid.app;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;

import org.aprsdroid.app.testing.CoordinateMatcher;
import org.junit.Test;

import scala.Tuple2;

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
    public void givenLocationInNEHemisphere_whenFormattedAsDMSString_thenParseBackIntoDecimalValue() {
        Tuple2<String, String> actual = AprsPacket$.MODULE$.formatCoordinates(expectedNLatitude, expectedELongitude);
        float floatLatitude = CoordinateMatcher.matchLatitude(actual._1);
        float floatLongitude = CoordinateMatcher.matchLongitude(actual._2);
        assertThat("Latitude", (double) floatLatitude, closeTo((double) expectedNLatitude, 1e-7));
        assertThat("Longitude", (double) floatLongitude, closeTo((double) expectedELongitude, 1e-7));
    }

    @Test
    public void givenLocationInNWHemisphere_whenFormattedAsDMSString_thenParseBackIntoDecimalValue() {
        Tuple2<String, String> actual = AprsPacket$.MODULE$.formatCoordinates(expectedNLatitude, expectedWLongitude);
        float floatLatitude = CoordinateMatcher.matchLatitude(actual._1);
        float floatLongitude = CoordinateMatcher.matchLongitude(actual._2);
        assertThat("Latitude", (double) floatLatitude, closeTo((double) expectedNLatitude, 1e-7));
        assertThat("Longitude", (double) floatLongitude, closeTo((double) expectedWLongitude, 1e-7));
    }

    @Test
    public void givenLocationInSEHemisphere_whenFormattedAsDMSString_thenParseBackIntoDecimalValue() {
        Tuple2<String, String> actual = AprsPacket$.MODULE$.formatCoordinates(expectedSLatitude, expectedELongitude);
        float floatLatitude = CoordinateMatcher.matchLatitude(actual._1);
        float floatLongitude = CoordinateMatcher.matchLongitude(actual._2);
        assertThat("Latitude", (double) floatLatitude, closeTo((double) expectedSLatitude, 1e-7));
        assertThat("Longitude", (double) floatLongitude, closeTo((double) expectedELongitude, 1e-7));
    }

    @Test
    public void givenLocationInSWHemisphere_whenFormattedAsDMSString_thenParseBackIntoDecimalValue() {
        Tuple2<String, String> actual = AprsPacket$.MODULE$.formatCoordinates(expectedSLatitude, expectedWLongitude);
        float floatLatitude = CoordinateMatcher.matchLatitude(actual._1);
        float floatLongitude = CoordinateMatcher.matchLongitude(actual._2);
        assertThat("Latitude", (double) floatLatitude, closeTo((double) expectedSLatitude, 1e-7));
        assertThat("Longitude", (double) floatLongitude, closeTo((double) expectedWLongitude, 1e-7));
    }
}