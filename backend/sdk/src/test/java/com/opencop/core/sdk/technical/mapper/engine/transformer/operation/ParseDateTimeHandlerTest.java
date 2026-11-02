// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import static org.junit.jupiter.api.Assertions.*;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import com.opencop.core.sdk.model.mapper.transform.TransformStep;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ParseDateTimeHandlerTest {

  private final ParseDateTimeHandler handler = new ParseDateTimeHandler();

  @Test
  void operation_is_parse_date_time() {
    assertEquals(TransformOperation.PARSE_DATE_TIME, handler.operation());
  }

  @Test
  void returns_null_when_input_is_null() throws Exception {
    assertNull(handler.apply(null, step(null, null)));
  }

  @Test
  void returns_null_when_input_is_blank() throws Exception {
    assertNull(handler.apply("   ", step(null, null)));
  }

  @Test
  void parses_iso_instant() throws Exception {
    Object out = handler.apply("2024-01-15T10:30:45Z", step(null, null));
    assertEquals(Instant.parse("2024-01-15T10:30:45Z"), out);
  }

  @Test
  void parses_iso_offset_datetime_to_instant() throws Exception {
    Object out = handler.apply("2024-01-15T05:30:45-05:00", step(null, null));
    assertEquals(Instant.parse("2024-01-15T10:30:45Z"), out);
  }

  @Test
  void parses_custom_format_with_zone_id_from_step_value() throws Exception {
    TransformStep s = step("yyyyMMdd HHmmss", "America/New_York");
    Object out = handler.apply("20240115 053045", s);

    // 05:30:45 in America/New_York (UTC-05 in Jan) => 10:30:45Z
    assertEquals(Instant.parse("2024-01-15T10:30:45Z"), out);
  }

  @Test
  void throws_when_zone_id_is_invalid() {
    TransformStep s = step("yyyyMMdd HHmmss", "NOT_A_ZONE");

    MapperEngineException ex =
        assertThrows(MapperEngineException.class, () -> handler.apply("20240115 053045", s));

    assertEquals("TRANSFORM_ERROR", ex.getCode());
    assertTrue(ex.getMessage().contains("Cannot resolve ZoneId"));
  }

  @Test
  void throws_when_custom_format_cannot_parse() {
    TransformStep s = step("yyyyMMdd HHmmss", "America/New_York");

    MapperEngineException ex =
        assertThrows(MapperEngineException.class, () -> handler.apply("not a date", s));

    assertEquals("TRANSFORM_ERROR", ex.getCode());
  }

  @Test
  void throws_when_iso_has_no_zone_or_offset() {
    MapperEngineException ex =
        assertThrows(
            MapperEngineException.class,
            () -> handler.apply("2024-01-15T10:30:45", step(null, null)));

    assertEquals("TRANSFORM_ERROR", ex.getCode());
  }

  private static TransformStep step(String format, String valueZoneId) {
    return new TransformStep(
        TransformOperation.PARSE_DATE_TIME, null, null, format, valueZoneId, null, null);
  }
}
