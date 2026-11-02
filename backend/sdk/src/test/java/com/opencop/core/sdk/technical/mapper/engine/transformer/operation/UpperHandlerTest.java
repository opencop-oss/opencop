// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import static org.junit.jupiter.api.Assertions.*;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import com.opencop.core.sdk.model.mapper.transform.TransformStep;
import com.opencop.core.sdk.model.mapper.transform.TransformStepBuilder;
import org.junit.jupiter.api.Test;

class UpperHandlerTest {

  private final UpperHandler handler = new UpperHandler();

  @Test
  void operation_is_upper() {
    assertEquals(TransformOperation.UPPER, handler.operation());
  }

  @Test
  void uppercases_string() {
    TransformStep step = TransformStepBuilder.upper();
    Object out = handler.apply("Hello World", step);
    assertEquals("HELLO WORLD", out);
  }

  @Test
  void returns_null_when_input_is_null() {
    TransformStep step = TransformStepBuilder.upper();
    assertNull(handler.apply(null, step));
  }

  @Test
  void non_string_is_converted_to_string_and_uppercased() {
    TransformStep step = TransformStepBuilder.upper();
    Object out = handler.apply("abc123", step);
    assertEquals("ABC123", out);
  }
}
