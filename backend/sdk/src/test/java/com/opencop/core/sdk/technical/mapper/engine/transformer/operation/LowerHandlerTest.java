// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import static org.junit.jupiter.api.Assertions.*;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import org.junit.jupiter.api.Test;

class LowerHandlerTest {

  private final LowerHandler handler = new LowerHandler();

  @Test
  void operation_is_lower() {
    assertEquals(TransformOperation.LOWER, handler.operation());
  }

  @Test
  void lowercases_string() {
    Object out = handler.apply("HeLLo WoRLD", null);
    assertEquals("hello world", out);
  }

  @Test
  void returns_null_when_input_is_null() {
    Object out = handler.apply(null, null);
    assertNull(out);
  }

  @Test
  void non_string_is_converted_to_string_and_lowercased() {
    Object out = handler.apply(12345, null);
    assertEquals("12345", out);
  }
}
