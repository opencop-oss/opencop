// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import com.opencop.core.sdk.model.mapper.transform.TransformStep;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import org.springframework.stereotype.Component;

@Component
public class ParseDateTimeHandler implements TransformHandler {

  public TransformOperation operation() {
    return TransformOperation.PARSE_DATE_TIME;
  }

  public Object apply(Object in, TransformStep step) throws MapperEngineException {
    String value = in == null ? null : String.valueOf(in);
    if (value == null || value.isBlank()) {
      return null;
    }

    String format = step.format();

    // Try ISO variants
    if (format == null || format.isBlank()) {
      String normalized = value.replaceFirst("([+-]\\d{2})(\\d{2})$", "$1:$2");
      try {
        return Instant.parse(normalized);
      } catch (Exception _) {
      }
      try {
        return OffsetDateTime.parse(normalized).toInstant();
      } catch (Exception _) {
      }
      try {
        return ZonedDateTime.parse(normalized).toInstant();
      } catch (Exception _) {
      }

      throw new MapperEngineException("TRANSFORM_ERROR", "Cannot map a null value to UTC", null);
    }

    String zoneId = step.value();
    ZoneId zone;
    try {
      zone = ZoneId.of(zoneId);
    } catch (DateTimeException e) {
      throw new MapperEngineException(
          "TRANSFORM_ERROR", "Cannot resolve ZoneId from value: " + zoneId, e);
    }

    try {
      LocalDateTime localDateTime = LocalDateTime.parse(value);
      return localDateTime.atZone(ZoneId.of(zoneId)).toInstant();
    } catch (Exception _) {
    }

    try {
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
      TemporalAccessor accessor = formatter.parse(value);

      if (accessor.isSupported(ChronoField.OFFSET_SECONDS)) {
        return OffsetDateTime.from(accessor).toInstant();
      }
      return LocalDateTime.from(accessor).atZone(zone).toInstant();
    } catch (Exception _) {
      throw new MapperEngineException("TRANSFORM_ERROR", "Cannot map a null value to UTC", null);
    }
  }
}
