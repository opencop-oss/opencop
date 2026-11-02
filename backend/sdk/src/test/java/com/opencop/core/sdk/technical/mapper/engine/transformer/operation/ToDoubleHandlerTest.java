// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import static org.junit.jupiter.api.Assertions.*;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import com.opencop.core.sdk.model.mapper.transform.TransformStep;
import com.opencop.core.sdk.model.mapper.transform.TransformStepBuilder;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import org.junit.jupiter.api.Test;

class ToDoubleHandlerTest {

  private final ToDoubleHandler handler = new ToDoubleHandler();

  @Test
  void operation_is_to_double() {
    assertEquals(TransformOperation.TO_DOUBLE, handler.operation());
  }

  @Test
  void returns_null_when_input_is_null() throws MapperEngineException {
    TransformStep step = TransformStepBuilder.op(TransformOperation.TO_DOUBLE).build();
    assertNull(handler.apply(null, step));
  }

  @Test
  void passes_through_double() throws MapperEngineException {
    TransformStep step = TransformStepBuilder.op(TransformOperation.TO_DOUBLE).build();
    Object out = handler.apply(12.34d, step);
    assertEquals(12.34d, out);
  }

  @Test
  void converts_number_to_double() throws MapperEngineException {
    TransformStep step = TransformStepBuilder.op(TransformOperation.TO_DOUBLE).build();
    Object out = handler.apply(5, step);
    assertEquals(5.0d, out);
  }

  @Test
  void parses_string_to_double() throws MapperEngineException {
    TransformStep step = TransformStepBuilder.op(TransformOperation.TO_DOUBLE).build();
    Object out = handler.apply("  45.67  ", step);
    assertEquals(45.67d, out);
  }

  @Test
  void throws_for_blank_string() {
    TransformStep step = TransformStepBuilder.op(TransformOperation.TO_DOUBLE).build();

    MapperEngineException ex =
        assertThrows(MapperEngineException.class, () -> handler.apply("   ", step));

    assertEquals("TRANSFORM_ERROR", ex.getCode());
  }

  @Test
  void throws_for_invalid_string() {
    TransformStep step = TransformStepBuilder.op(TransformOperation.TO_DOUBLE).build();

    MapperEngineException ex =
        assertThrows(MapperEngineException.class, () -> handler.apply("not-a-number", step));

    assertEquals("TRANSFORM_ERROR", ex.getCode());
    assertTrue(ex.getMessage().contains("Cannot convert value to double"));
  }
}
