// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.business.policy.rtsp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputException;
import org.geojson.LngLatAlt;
import org.geojson.Point;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RtspCoordinatesPolicyTest {

  @Test
  void placesTheCameraAtTheSuppliedPoint() throws ConnectionInputException {
    assertThat(RtspCoordinatesPolicy.cameraPosition("45.5", "-73.6"))
        .contains(new Point(new LngLatAlt(-73.6, 45.5)));
  }

  @Test
  void placesNoCameraWhenNeitherCoordinateIsSupplied() throws ConnectionInputException {
    assertThat(RtspCoordinatesPolicy.cameraPosition(null, " ")).isEmpty();
  }

  @ParameterizedTest
  @CsvSource(
      nullValues = "NULL",
      value = {"45.5,NULL,longitude", "NULL,-73.6,latitude"})
  void requiresBothCoordinatesOrNeither(String latitude, String longitude, String missingField) {
    assertRejected(latitude, longitude, RtspCoordinatesPolicy.INCOMPLETE_COORDINATES, missingField);
  }

  @ParameterizedTest
  @CsvSource({"90.1,0,latitude", "-90.1,0,latitude", "0,180.1,longitude", "0,-180.1,longitude"})
  void rejectsACoordinateOutsideItsRange(String latitude, String longitude, String field) {
    assertRejected(latitude, longitude, RtspCoordinatesPolicy.INVALID_COORDINATE, field);
  }

  @ParameterizedTest
  @CsvSource({"north,0,latitude", "0,NaN,longitude"})
  void rejectsACoordinateThatIsNotANumber(String latitude, String longitude, String field) {
    assertRejected(latitude, longitude, RtspCoordinatesPolicy.INVALID_COORDINATE, field);
  }

  private static void assertRejected(
      String latitude, String longitude, String detailCode, String field) {
    assertThatThrownBy(() -> RtspCoordinatesPolicy.cameraPosition(latitude, longitude))
        .isInstanceOfSatisfying(
            ConnectionInputException.class,
            exception -> {
              assertThat(exception.providerDetailCode()).contains(detailCode);
              assertThat(exception.field()).contains(field);
            });
  }
}
