// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.watchbox;

/**
 * Counter identities for one {@link WatchboxBatchResult}. Keyed access replaces a positional run of
 * identically-typed record components; the engine's metric mapping switches over this enum
 * exhaustively, so adding a counter forces a compile-time decision about how it is reported.
 */
public enum WatchboxBatchOutcome {
  SEEDED,
  RESEEDED_EXPIRED,
  ENTERED,
  LEFT,
  FILTER_MATCH_GAINED,
  FILTER_MATCH_LOST,
  INSIDE_COMPARISONS,
  REFRESHED,
  STALE_DROPPED,
  STALE_DROPPED_TRANSITION,
  DUPLICATE_DROPPED,
  FROZEN_DROPPED,
  SUPPRESSED_BY_DIRECTION,
  TRIPPED,
  DETECTIONS_EMITTED,
  DETECTIONS_SUPPRESSED_DUPLICATE,
  DETECTIONS_SUPPRESSED_LOST_FOLD
}
