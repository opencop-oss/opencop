// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.broker.kafka;

/**
 * Shared Kafka naming constants used across modules (core, processor).
 *
 * <p>Azure Event Hubs lowercases entity names at creation time. All topic name components (prefix,
 * slug, UUID, DLT suffix) must be lowercase so Azure's normalization is a no-op.
 */
public final class KafkaNaming {

  /** Topic name pattern: opencop.byod.{slug}.{uuid} */
  public static final String TOPIC_TEMPLATE = "opencop.byod.%s.%s";

  /**
   * Dead-letter topic suffix. Lowercase to align with Azure Event Hubs naming (Azure lowercases
   * entity names at creation). Local Kafka preserves case as-is. Spring Kafka 4.0 defaults to
   * "-dlt" but we use ".dlt" (period delimiter) for consistency with our dot-delimited topic
   * naming: opencop.byod.{slug}.{uuid}.dlt
   */
  public static final String DLT_SUFFIX = ".dlt";

  private KafkaNaming() {}
}
