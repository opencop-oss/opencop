// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import static org.junit.jupiter.api.Assertions.*;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import com.opencop.core.sdk.model.mapper.transform.TransformStep;
import com.opencop.core.sdk.model.mapper.transform.TransformStepBuilder;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import org.junit.jupiter.api.Test;

class ToIntHandlerTest {

  private final ToIntHandler handler = new ToIntHandler();

  @Test
  void operation_is_to_int() {
    assertEquals(TransformOperation.TO_INT, handler.operation());
  }

  @Test
  void returns_null_when_input_is_null() throws MapperEngineException {
    TransformStep step = TransformStepBuilder.op(TransformOperation.TO_INT).build();
    assertNull(handler.apply(null, step));
  }

  @Test
  void passes_through_integer() throws MapperEngineException {
    TransformStep step = TransformStepBuilder.op(TransformOperation.TO_INT).build();
    Object out = handler.apply(42, step);
    assertEquals(42, out);
  }

  @Test
  void converts_number_to_int() throws MapperEngineException {
    TransformStep step = TransformStepBuilder.op(TransformOperation.TO_INT).build();
    Object out = handler.apply(42.9, step);
    assertEquals(42, out);
  }

  @Test
  void parses_string_to_int() throws MapperEngineException {
    TransformStep step = TransformStepBuilder.op(TransformOperation.TO_INT).build();
    Object out = handler.apply("  123  ", step);
    assertEquals(123, out);
  }

  @Test
  void throws_for_blank_string() {
    TransformStep step = TransformStepBuilder.op(TransformOperation.TO_INT).build();

    MapperEngineException ex =
        assertThrows(MapperEngineException.class, () -> handler.apply("   ", step));

    assertEquals("TRANSFORM_ERROR", ex.getCode());
  }

  @Test
  void throws_for_invalid_string() {
    TransformStep step = TransformStepBuilder.op(TransformOperation.TO_INT).build();

    MapperEngineException ex =
        assertThrows(MapperEngineException.class, () -> handler.apply("not-an-int", step));

    assertEquals("TRANSFORM_ERROR", ex.getCode());
    assertTrue(ex.getMessage().contains("Cannot convert value to int"));
  }
}
