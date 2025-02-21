/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.common.geo;

import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.unit.DistanceUnit;
import org.opensearch.test.OpenSearchTestCase;

import java.io.IOException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;

/**
 * Basic Tests for {@link GeoDistance}
 */
public class GeoDistanceTests extends OpenSearchTestCase {

    public void testGeoDistanceSerialization() throws IOException  {
        // make sure that ordinals don't change, because we rely on then in serialization
        assertThat(GeoDistance.PLANE.ordinal(), equalTo(0));
        assertThat(GeoDistance.ARC.ordinal(), equalTo(1));
        assertThat(GeoDistance.values().length, equalTo(2));

        GeoDistance geoDistance = randomFrom(GeoDistance.PLANE, GeoDistance.ARC);
        try (BytesStreamOutput out = new BytesStreamOutput()) {
            geoDistance.writeTo(out);
            try (StreamInput in = out.bytes().streamInput()) {
                GeoDistance copy = GeoDistance.readFromStream(in);
                assertEquals(copy.toString() + " vs. " + geoDistance.toString(), copy, geoDistance);
            }
        }
    }

    public void testInvalidReadFrom() throws Exception {
        try (BytesStreamOutput out = new BytesStreamOutput()) {
            if (randomBoolean()) {
                out.writeVInt(randomIntBetween(GeoDistance.values().length, Integer.MAX_VALUE));
            } else {
                out.writeVInt(randomIntBetween(Integer.MIN_VALUE, -1));
            }
            try (StreamInput in = out.bytes().streamInput()) {
                GeoDistance.readFromStream(in);
            } catch (IOException e) {
                assertThat(e.getMessage(), containsString("Unknown GeoDistance ordinal ["));
            }
        }
    }

    private static double arcDistance(GeoPoint p1, GeoPoint p2) {
        return GeoDistance.ARC.calculate(p1.lat(), p1.lon(), p2.lat(), p2.lon(), DistanceUnit.METERS);
    }

    private static double planeDistance(GeoPoint p1, GeoPoint p2) {
        return GeoDistance.PLANE.calculate(p1.lat(), p1.lon(), p2.lat(), p2.lon(), DistanceUnit.METERS);
    }

    public void testArcDistanceVsPlane() {
        // sameLongitude and sameLatitude are both 90 degrees away from basePoint along great circles
        final GeoPoint basePoint = new GeoPoint(45, 90);
        final GeoPoint sameLongitude = new GeoPoint(-45, 90);
        final GeoPoint sameLatitude = new GeoPoint(45, -90);

        double sameLongitudeArcDistance = arcDistance(basePoint, sameLongitude);
        double sameLatitudeArcDistance = arcDistance(basePoint, sameLatitude);
        double sameLongitudePlaneDistance = planeDistance(basePoint, sameLongitude);
        double sameLatitudePlaneDistance = planeDistance(basePoint, sameLatitude);

        // GeoDistance.PLANE measures the distance along a straight line in
        // (lat, long) space so agrees with GeoDistance.ARC along a line of
        // constant longitude but takes a longer route if there is east/west
        // movement.

        assertThat("Arc and plane should agree on sameLongitude",
            Math.abs(sameLongitudeArcDistance - sameLongitudePlaneDistance), lessThan(0.001));

        assertThat("Arc and plane should disagree on sameLatitude (by >4000km)",
            sameLatitudePlaneDistance - sameLatitudeArcDistance, greaterThan(4.0e6));

        // GeoDistance.ARC calculates the great circle distance (on a sphere) so these should agree as they're both 90 degrees
        assertThat("Arc distances should agree", Math.abs(sameLongitudeArcDistance - sameLatitudeArcDistance), lessThan(0.001));
    }

    public void testArcDistanceVsPlaneAccuracy() {
        // These points only differ by a few degrees so the calculation methods
        // should match more closely. Check that the deviation is small enough,
        // but not too small.

        // The biggest deviations are away from the equator and the poles so pick a suitably troublesome latitude.
        GeoPoint basePoint = new GeoPoint(randomDoubleBetween(30.0, 60.0, true), randomDoubleBetween(-180.0, 180.0, true));
        GeoPoint sameLongitude = new GeoPoint(randomDoubleBetween(-90.0, 90.0, true), basePoint.lon());
        GeoPoint sameLatitude = new GeoPoint(basePoint.lat(), basePoint.lon() + randomDoubleBetween(4.0, 10.0, true));

        double sameLongitudeArcDistance = arcDistance(basePoint, sameLongitude);
        double sameLatitudeArcDistance = arcDistance(basePoint, sameLatitude);
        double sameLongitudePlaneDistance = planeDistance(basePoint, sameLongitude);
        double sameLatitudePlaneDistance = planeDistance(basePoint, sameLatitude);

        assertThat("Arc and plane should agree [" + basePoint + "] to [" + sameLongitude + "] (within 1cm)",
            Math.abs(sameLongitudeArcDistance - sameLongitudePlaneDistance), lessThan(0.01));

        assertThat("Arc and plane should very roughly agree [" + basePoint + "] to [" + sameLatitude + "]",
            sameLatitudePlaneDistance - sameLatitudeArcDistance, lessThan(600.0));

        assertThat("Arc and plane should disagree by some margin [" + basePoint + "] to [" + sameLatitude + "]",
            sameLatitudePlaneDistance - sameLatitudeArcDistance, greaterThan(15.0));
    }
}
