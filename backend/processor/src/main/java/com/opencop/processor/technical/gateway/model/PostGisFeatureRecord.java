// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.model;

import com.opencop.core.sdk.model.provenance.FeatureProvenance;
import java.time.Instant;
import java.util.UUID;

public record PostGisFeatureRecord(
    UUID id,
    String kind,
    UUID source,
    UUID authorId,
    String objectId,
    Instant startAt,
    Instant staleAt,
    String geometryJson,
    String payloadJson,
    FeatureProvenance provenance) {

  public PostGisFeatureRecord {
    provenance = provenance == null ? FeatureProvenance.NONE : provenance;
  }

  /** Without provenance, for entry points that persist features outside the ingest pipeline. */
  public PostGisFeatureRecord(
      UUID id,
      String kind,
      UUID source,
      UUID authorId,
      String objectId,
      Instant startAt,
      Instant staleAt,
      String geometryJson,
      String payloadJson) {
    this(
        id,
        kind,
        source,
        authorId,
        objectId,
        startAt,
        staleAt,
        geometryJson,
        payloadJson,
        FeatureProvenance.NONE);
  }
}
