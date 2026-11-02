// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.event;

import static com.opencop.core.sdk.technical.mapper.engine.FeatureBuilder.DEFAULT_STALE_DAYS_OFFSET;

import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.Timestampz;
import java.time.Duration;
import java.time.Instant;

public final class FeatureStartAtSupport {

  private FeatureStartAtSupport() {}

  public static void ensureStartAt(OpenCopProperties properties) {
    if (properties == null) {
      return;
    }

    Timestampz timestampz = properties.getTimestampz();
    if (timestampz == null) {
      Instant now = Instant.now();
      properties.setTimestampz(
          new Timestampz(
              now.toEpochMilli(),
              now.toEpochMilli(),
              now.plus(Duration.ofDays(DEFAULT_STALE_DAYS_OFFSET)).toEpochMilli()));
      return;
    }

    if (timestampz.getStart() > 0) {
      return;
    }

    long fallback = timestampz.getTime() > 0 ? timestampz.getTime() : Instant.now().toEpochMilli();
    timestampz.setStart(fallback);
  }
}
