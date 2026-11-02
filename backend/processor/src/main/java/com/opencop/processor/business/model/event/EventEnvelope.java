// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.event;

import java.util.Map;
import java.util.Objects;

/**
 * An event plus the metadata a business stage must carry forward without interpreting it.
 *
 * <p>Transport-neutral by construction: {@code metadata} is a plain string map, so a business stage
 * can forward it to the next topic without naming Kafka types or knowing what any entry means. The
 * adapter that consumed the record decides which metadata is allowed through; a stage that drops an
 * event drops its metadata with it, which is what keeps rejected events out of anything derived
 * downstream.
 *
 * @param event the event being processed
 * @param metadata already-allowlisted metadata to re-emit on republish, never null
 */
public record EventEnvelope<T>(T event, Map<String, String> metadata) {

  public EventEnvelope {
    Objects.requireNonNull(event, "event");
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }

  /** For callers with no metadata to carry, such as tests and non-Kafka entry points. */
  public static <T> EventEnvelope<T> of(T event) {
    return new EventEnvelope<>(event, Map.of());
  }
}
