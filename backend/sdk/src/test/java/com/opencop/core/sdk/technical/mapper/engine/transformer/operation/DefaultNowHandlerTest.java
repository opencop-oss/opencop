// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import static org.junit.jupiter.api.Assertions.*;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import com.opencop.core.sdk.model.mapper.transform.TransformStepBuilder;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DefaultNowHandlerTest {

  private final DefaultNowHandler handler = new DefaultNowHandler();

  @Test
  void operation_is_default_now() {
    assertEquals(TransformOperation.DEFAULT_NOW, handler.operation());
  }

  @Test
  void accepts_null_input() {
    assertTrue(handler.acceptsNullInput());
  }

  @Test
  void returns_now_when_input_is_null() {
    Instant before = Instant.now();
    Object out = handler.apply(null, TransformStepBuilder.defaultNow());
    Instant after = Instant.now();

    assertInstanceOf(Instant.class, out);
    assertFalse(((Instant) out).isBefore(before));
    assertFalse(((Instant) out).isAfter(after));
  }

  @Test
  void returns_now_when_input_is_blank() {
    Instant before = Instant.now();
    Object out = handler.apply("   ", TransformStepBuilder.defaultNow());
    Instant after = Instant.now();

    assertInstanceOf(Instant.class, out);
    assertFalse(((Instant) out).isBefore(before));
    assertFalse(((Instant) out).isAfter(after));
  }

  @Test
  void preserves_existing_value_when_present() {
    assertEquals(
        "2024-01-15T10:30:45Z",
        handler.apply("2024-01-15T10:30:45Z", TransformStepBuilder.defaultNow()));
  }
}
