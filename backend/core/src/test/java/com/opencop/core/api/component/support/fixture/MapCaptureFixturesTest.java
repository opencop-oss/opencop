// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.support.fixture;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class MapCaptureFixturesTest {

  @Test
  void alternateMapViewUsesReviewedUsReferenceCoordinates() {
    var mapView = MapCaptureFixtures.alternateMapView();

    assertAll(
        () -> assertEquals(39.9526, mapView.latitude()),
        () -> assertEquals(-75.1652, mapView.longitude()),
        () -> assertNotEquals(46.8139, mapView.latitude()),
        () -> assertNotEquals(-71.2080, mapView.longitude()));
  }
}
