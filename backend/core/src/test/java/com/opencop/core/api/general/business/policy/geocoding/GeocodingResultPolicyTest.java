// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.geocoding;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.api.general.business.model.geocoding.PlaceDefect;
import com.opencop.core.sdk.business.model.integration.geocoding.BoundingBox;
import com.opencop.core.sdk.business.model.integration.geocoding.GeoPoint;
import com.opencop.core.sdk.business.model.integration.geocoding.GeocodedPlace;
import com.opencop.core.sdk.business.model.integration.geocoding.GeocodingAnswer;
import com.opencop.core.sdk.business.model.integration.geocoding.PlaceType;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GeocodingResultPolicyTest {

  private static final int LIMIT = 10;
  private static final GeoPoint ORIGIN = new GeoPoint(0, 0);

  @ParameterizedTest
  @CsvSource({"90, 180", "-90, -180", "0, 0", "-0.0, 179.999"})
  void keepsAPositionWithinWgs84Ranges(double lat, double lon) {
    assertThat(defects(place(new GeoPoint(lat, lon), Optional.empty()))).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "90.0001, 0",
    "-90.0001, 0",
    "0, 180.0001",
    "0, -180.0001",
    "NaN, 0",
    "0, NaN",
    "Infinity, 0",
    "0, -Infinity"
  })
  void dropsAPositionOutsideWgs84Ranges(double lat, double lon) {
    assertThat(defects(place(new GeoPoint(lat, lon), Optional.empty())))
        .containsExactly(PlaceDefect.INVALID_POSITION);
  }

  @ParameterizedTest
  @CsvSource({
    "-10, -20, 10, 20",
    "10, 170, 20, -170",
    "-90, -180, 90, 180",
    "5, 5, 5, 5",
  })
  void keepsAWellFormedBoundingBox(double minLat, double minLon, double maxLat, double maxLon) {
    var bbox = new BoundingBox(minLat, minLon, maxLat, maxLon);

    assertThat(defects(place(ORIGIN, Optional.of(bbox)))).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "20, -20, 10, 20",
    "-91, -20, 10, 20",
    "-10, -181, 10, 20",
    "-10, -20, 91, 20",
    "-10, -20, 10, 181",
    "NaN, -20, 10, 20",
    "-10, -20, 10, Infinity",
  })
  void dropsAMalformedBoundingBox(double minLat, double minLon, double maxLat, double maxLon) {
    var bbox = new BoundingBox(minLat, minLon, maxLat, maxLon);

    assertThat(defects(place(ORIGIN, Optional.of(bbox)))).containsExactly(PlaceDefect.INVALID_BBOX);
  }

  @Test
  void dropsAMissingPlace() {
    assertThat(defects(null)).containsExactly(PlaceDefect.MISSING_PLACE);
  }

  private static List<PlaceDefect> defects(GeocodedPlace place) {
    var answer = new GeocodingAnswer(Arrays.asList(place));
    return GeocodingResultPolicy.screen(answer, LIMIT).orElseThrow().defects();
  }

  private static GeocodedPlace place(GeoPoint position, Optional<BoundingBox> bbox) {
    return new GeocodedPlace(
        "R1",
        "Place",
        "Place, Region",
        PlaceType.LOCALITY,
        Optional.empty(),
        position,
        bbox,
        List.of("© Example contributors"));
  }
}
