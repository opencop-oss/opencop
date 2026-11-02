// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class BoundingBoxTest {

  @Test
  void parse_returnsNullWhenBlank() {
    assertNull(BoundingBox.parse(" "));
  }

  @Test
  void parse_normalizesWrappedLongitudes() {
    BoundingBox bbox = BoundingBox.parse("181,45,539,46");

    assertEquals(-179.0d, bbox.minLon());
    assertEquals(45.0d, bbox.minLat());
    assertEquals(179.0d, bbox.maxLon());
    assertEquals(46.0d, bbox.maxLat());
  }

  @Test
  void parse_rejectsLatitudeOutsideWgs84Range() {
    var exception =
        assertThrows(GlobalOpenCopException.class, () -> BoundingBox.parse("-73,91,-72,92"));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    assertEquals("'bbox' latitude values must be between -90 and 90", exception.getMessage());
  }

  @Test
  void parse_allowsAntimeridianCrossingBoxAfterNormalization() {
    BoundingBox bbox = BoundingBox.parse("170,10,-170,20");

    assertEquals(170.0d, bbox.minLon());
    assertEquals(-170.0d, bbox.maxLon());
    assertTrue(bbox.crossesAntimeridian());
  }
}
