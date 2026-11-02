// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import static org.junit.jupiter.api.Assertions.*;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import com.opencop.core.sdk.model.mapper.transform.TransformStep;
import com.opencop.core.sdk.model.mapper.transform.TransformStepBuilder;
import org.junit.jupiter.api.Test;

class RegexHandlerTest {

  private final RegexHandler handler = new RegexHandler();

  @Test
  void operation_is_replace_regex() {
    assertEquals(TransformOperation.REPLACE_REGEX, handler.operation());
  }

  @Test
  void replaces_all_matches() {
    TransformStep step = TransformStepBuilder.replaceRegex("-", "");

    Object out = handler.apply("10-0-3-10-0", step);

    assertEquals("1003100", out);
  }

  @Test
  void returns_input_when_pattern_is_null() {
    TransformStep step = TransformStepBuilder.op(TransformOperation.REPLACE_REGEX).build();

    Object out = handler.apply("ABC-123", step);

    assertEquals("ABC-123", out);
  }

  @Test
  void uses_empty_string_when_replacement_is_null() {
    TransformStep step =
        TransformStepBuilder.op(TransformOperation.REPLACE_REGEX).pattern("\\s+").build();

    Object out = handler.apply("A B   C", step);

    assertEquals("ABC", out);
  }

  @Test
  void returns_null_when_input_is_null() {
    TransformStep step = TransformStepBuilder.replaceRegex("-", "");

    Object out = handler.apply(null, step);

    assertNull(out);
  }

  @Test
  void non_string_input_is_converted_to_string() {
    TransformStep step = TransformStepBuilder.replaceRegex("0", "X");

    Object out = handler.apply(1002, step);

    assertEquals("1XX2", out);
  }
}
