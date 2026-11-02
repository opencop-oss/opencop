// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.provenance;

import java.time.Instant;
import java.util.UUID;

/**
 * Where a persisted feature came from: which mapper produced it, from which inbound message, and
 * when each of those happened.
 *
 * <p>Every component is nullable. Features predating provenance keep null values, and a duplicate
 * arriving without it must never overwrite what is already known — so a null here means "not
 * reported", never "known to be absent".
 *
 * <p>The three timestamps are deliberately distinct events and must not be substituted for one
 * another: {@code sourceReceivedAt} is when the message arrived, {@code mappingCompletedAt} is when
 * the mapper finished with it, and commit time is {@code geo.features.created_at}. Resource
 * activity reads the first two; using commit time instead would report when the database noticed
 * rather than when anything happened.
 *
 * @param sourceKind whether {@code geo.features.source} names a data source or a layer
 * @param mapperId the mapper that produced the feature
 * @param mapperVersion the mapper version that ran, which the mapper id alone does not pin down
 * @param ingressId stable identity of the inbound message; preserved across transport retries, and
 *     retained by an intentional remap even though that takes a new {@code mappingExecutionId}
 * @param mappingExecutionId stable identity of one mapping execution
 * @param sourceReceivedAt when the inbound message was received
 * @param mappingCompletedAt when the mapping execution finished
 */
public record FeatureProvenance(
    FeatureSourceKind sourceKind,
    UUID mapperId,
    Integer mapperVersion,
    String ingressId,
    String mappingExecutionId,
    Instant sourceReceivedAt,
    Instant mappingCompletedAt) {

  /** Nothing known, for paths that persist features without provenance. */
  public static final FeatureProvenance NONE =
      new FeatureProvenance(null, null, null, null, null, null, null);

  /** Whether every component is absent, so callers can skip attaching empty header sets. */
  public boolean isEmpty() {
    return sourceKind == null
        && mapperId == null
        && mapperVersion == null
        && ingressId == null
        && mappingExecutionId == null
        && sourceReceivedAt == null
        && mappingCompletedAt == null;
  }

  /** What {@code geo.features.source} refers to, matching the column's CHECK constraint. */
  public enum FeatureSourceKind {
    DATA_SOURCE,
    LAYER
  }
}
