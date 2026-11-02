// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import com.opencop.core.sdk.model.mapper.transform.TransformStep;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Component;

@Component
public class AddDurationHandler implements TransformHandler {

  @Override
  public TransformOperation operation() {
    return TransformOperation.ADD_DURATION;
  }

  @Override
  public Object apply(Object in, TransformStep step) throws MapperEngineException {
    if (in == null) {
      return null;
    }

    String rawDuration = step.value();
    if (rawDuration == null || rawDuration.isBlank()) {
      throw new MapperEngineException(
          "TRANSFORM_ERROR", "Cannot add a duration without a configured value", null);
    }

    Duration duration;
    try {
      duration = Duration.parse(rawDuration);
    } catch (Exception e) {
      throw new MapperEngineException(
          "TRANSFORM_ERROR", "Cannot parse duration from value: " + rawDuration, e);
    }

    Instant instant = toInstant(in);
    return instant.plus(duration);
  }

  private Instant toInstant(Object in) throws MapperEngineException {
    if (in instanceof Instant instant) {
      return instant;
    }
    if (in instanceof OffsetDateTime offsetDateTime) {
      return offsetDateTime.toInstant();
    }
    if (in instanceof ZonedDateTime zonedDateTime) {
      return zonedDateTime.toInstant();
    }

    String value = String.valueOf(in).trim();
    if (value.isEmpty()) {
      throw new MapperEngineException(
          "TRANSFORM_ERROR", "Cannot add a duration to an empty timestamp value", null);
    }

    try {
      return Instant.parse(value);
    } catch (Exception _) {
    }
    try {
      return OffsetDateTime.parse(value).toInstant();
    } catch (Exception _) {
    }
    try {
      return ZonedDateTime.parse(value).toInstant();
    } catch (Exception _) {
    }

    throw new MapperEngineException(
        "TRANSFORM_ERROR", "ADD_DURATION requires an Instant-compatible value", null);
  }
}
