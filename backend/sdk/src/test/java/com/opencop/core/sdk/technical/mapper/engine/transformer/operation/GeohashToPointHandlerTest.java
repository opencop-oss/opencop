// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import static org.junit.jupiter.api.Assertions.*;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import com.opencop.core.sdk.model.mapper.transform.TransformStepBuilder;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import org.junit.jupiter.api.Test;

class GeohashToPointHandlerTest {

  private final GeohashToPointHandler handler = new GeohashToPointHandler();

  @Test
  void operation_is_geohash_to_point() {
    assertEquals(TransformOperation.GEOHASH_TO_POINT, handler.operation());
  }

  @Test
  void returns_null_when_input_is_null_or_blank() throws Exception {
    assertNull(handler.apply(null, null));
    assertNull(handler.apply("   ", null));
  }

  @Test
  void converts_valid_geohash_to_point() throws Exception {
    Object out = handler.apply("ezs42", TransformStepBuilder.geohashToPoint());

    assertInstanceOf(org.geojson.Point.class, out);
    org.geojson.Point p = (org.geojson.Point) out;
    assertEquals(-5.6030273, p.getCoordinates().getLongitude(), 1e-6);
    assertEquals(42.6049805, p.getCoordinates().getLatitude(), 1e-6);
  }

  @Test
  void accepts_uppercase_input_and_short_hashes() throws Exception {
    org.geojson.Point uppercase =
        (org.geojson.Point) handler.apply("EZS42", TransformStepBuilder.geohashToPoint());
    assertEquals(-5.6030273, uppercase.getCoordinates().getLongitude(), 1e-6);
    assertEquals(42.6049805, uppercase.getCoordinates().getLatitude(), 1e-6);

    org.geojson.Point shortHash =
        (org.geojson.Point) handler.apply("u", TransformStepBuilder.geohashToPoint());
    assertEquals(22.5d, shortHash.getCoordinates().getLongitude(), 1e-9);
    assertEquals(67.5d, shortHash.getCoordinates().getLatitude(), 1e-9);
  }

  @Test
  void throws_mapper_engine_exception_on_invalid_geohash() {
    MapperEngineException ex =
        assertThrows(MapperEngineException.class, () -> handler.apply("bad!", null));

    assertEquals("TRANSFORM_ERROR", ex.getCode());
    assertTrue(ex.getMessage().contains("Invalid geohash text"));
  }
}
