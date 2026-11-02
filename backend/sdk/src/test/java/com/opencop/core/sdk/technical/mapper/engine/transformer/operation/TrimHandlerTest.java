// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import static org.junit.jupiter.api.Assertions.*;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import com.opencop.core.sdk.model.mapper.transform.TransformStep;
import com.opencop.core.sdk.model.mapper.transform.TransformStepBuilder;
import org.junit.jupiter.api.Test;

class TrimHandlerTest {

  private final TrimHandler handler = new TrimHandler();

  @Test
  void operation_is_trim() {
    assertEquals(TransformOperation.TRIM, handler.operation());
  }

  @Test
  void trims_string() {
    TransformStep step = TransformStepBuilder.trim();
    Object out = handler.apply("  hello world  ", step);
    assertEquals("hello world", out);
  }

  @Test
  void returns_null_when_input_is_null() {
    TransformStep step = TransformStepBuilder.trim();
    assertNull(handler.apply(null, step));
  }

  @Test
  void non_string_is_converted_to_string_and_trimmed() {
    TransformStep step = TransformStepBuilder.trim();
    Object out = handler.apply("  123  ", step);
    assertEquals("123", out);
  }
}
