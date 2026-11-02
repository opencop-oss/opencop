// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.usecase.event;

/**
 * Stable, machine-readable error codes for user-visible failures on the historical-tracks REST
 * surface. The enum name is the wire value — clients branch UI copy off {@code body.code} instead
 * of string-matching {@code body.message}.
 *
 * <p>Renaming a constant is a breaking wire change. Add new entries; don't rename existing ones.
 */
public enum HistoricalErrorCode {
  HISTORICAL_RANGE_INVALID,
  HISTORICAL_SOURCES_REQUIRED,
  HISTORICAL_SOURCES_CONTAINS_NULL,
  HISTORICAL_SOURCES_DUPLICATE,
  HISTORICAL_FILTERS_CONTAINS_NULL,
  HISTORICAL_RANGE_TOO_LARGE,
  HISTORICAL_TOO_MANY_CHUNKS,
  HISTORICAL_CONCURRENT_LIMIT,
  HISTORICAL_SUBJECT_FORBIDDEN,
  HISTORICAL_FILTERS_NOT_SUPPORTED,
  HISTORICAL_LAYER_NOT_SUPPORTED
}
