// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import static org.junit.jupiter.api.Assertions.*;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import com.opencop.core.sdk.model.mapper.transform.TransformStep;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MapValuesHandlerTest {

  private final MapValuesHandler handler = new MapValuesHandler();

  @Test
  void operation_is_map_values() {
    assertEquals(TransformOperation.MAP_VALUES, handler.operation());
  }

  @Test
  void maps_value_when_key_exists() {
    TransformStep step =
        new TransformStep(
            TransformOperation.MAP_VALUES,
            null,
            null,
            null,
            null,
            Map.of("A", "Alpha", "B", "Beta"),
            null);

    Object out = handler.apply("A", step);

    assertEquals("Alpha", out);
  }

  @Test
  void returns_default_when_key_does_not_exist() {
    TransformStep step =
        new TransformStep(
            TransformOperation.MAP_VALUES, null, null, null, null, Map.of("A", "Alpha"), "Unknown");

    Object out = handler.apply("B", step);

    assertEquals("Unknown", out);
  }

  @Test
  void returns_value_when_map_is_null() {
    TransformStep step =
        new TransformStep(TransformOperation.MAP_VALUES, null, null, null, null, null, null);

    Object out = handler.apply("X", step);

    assertEquals("X", out);
  }

  @Test
  void returns_null_when_input_is_null() {
    TransformStep step =
        new TransformStep(
            TransformOperation.MAP_VALUES, null, null, null, null, Map.of("A", "Alpha"), "Default");

    Object out = handler.apply(null, step);

    assertNull(out);
  }

  @Test
  void non_string_input_is_converted_to_string() {
    TransformStep step =
        new TransformStep(
            TransformOperation.MAP_VALUES, null, null, null, null, Map.of("1", "One"), null);

    Object out = handler.apply(1, step);

    assertEquals("One", out);
  }
}
