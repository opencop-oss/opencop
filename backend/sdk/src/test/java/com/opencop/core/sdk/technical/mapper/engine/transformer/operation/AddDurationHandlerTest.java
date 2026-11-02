// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import static org.junit.jupiter.api.Assertions.*;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import com.opencop.core.sdk.model.mapper.transform.TransformStep;
import com.opencop.core.sdk.model.mapper.transform.TransformStepBuilder;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AddDurationHandlerTest {

  private final AddDurationHandler handler = new AddDurationHandler();

  @Test
  void operation_is_add_duration() {
    assertEquals(TransformOperation.ADD_DURATION, handler.operation());
  }

  @Test
  void adds_duration_to_instant_input() throws Exception {
    Object out =
        handler.apply(
            Instant.parse("2024-01-15T10:30:45Z"), TransformStepBuilder.addDuration("PT15M"));

    assertEquals(Instant.parse("2024-01-15T10:45:45Z"), out);
  }

  @Test
  void adds_duration_to_iso_string_input() throws Exception {
    Object out =
        handler.apply("2024-01-15T05:30:45-05:00", TransformStepBuilder.addDuration("PT30M"));

    assertEquals(Instant.parse("2024-01-15T11:00:45Z"), out);
  }

  @Test
  void returns_null_when_input_is_null() throws Exception {
    assertNull(handler.apply(null, TransformStepBuilder.addDuration("PT5M")));
  }

  @Test
  void throws_when_duration_is_missing() {
    TransformStep step = TransformStepBuilder.op(TransformOperation.ADD_DURATION).build();

    MapperEngineException ex =
        assertThrows(MapperEngineException.class, () -> handler.apply(Instant.now(), step));

    assertEquals("TRANSFORM_ERROR", ex.getCode());
    assertTrue(ex.getMessage().contains("configured value"));
  }

  @Test
  void throws_when_duration_is_invalid() {
    MapperEngineException ex =
        assertThrows(
            MapperEngineException.class,
            () -> handler.apply(Instant.now(), TransformStepBuilder.addDuration("tomorrow")));

    assertEquals("TRANSFORM_ERROR", ex.getCode());
    assertTrue(ex.getMessage().contains("Cannot parse duration"));
  }

  @Test
  void throws_when_value_is_not_timestamp_compatible() {
    MapperEngineException ex =
        assertThrows(
            MapperEngineException.class,
            () -> handler.apply("not-a-timestamp", TransformStepBuilder.addDuration("PT5M")));

    assertEquals("TRANSFORM_ERROR", ex.getCode());
    assertTrue(ex.getMessage().contains("Instant-compatible"));
  }
}
