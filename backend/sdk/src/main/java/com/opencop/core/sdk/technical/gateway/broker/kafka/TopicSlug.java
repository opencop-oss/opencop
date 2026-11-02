// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.broker.kafka;

import java.util.regex.Pattern;

/**
 * Sanitizes a data source name into a Kafka-safe slug for topic and username construction.
 *
 * <p>Kafka topic names only allow ASCII alphanumerics, '.', '_', and '-'. Non-ASCII characters
 * (Chinese, accented, emoji, etc.) are stripped. Uniqueness is guaranteed by the UUID suffix in the
 * full topic name, not the slug.
 *
 * @see <a
 *     href="https://github.com/apache/kafka/blob/4d0c588539eb3e84ba6c051837c3f03069fae3f1/clients/src/main/java/org/apache/kafka/common/internals/Topic.java#L35">Topic.java
 *     LEGAL_CHARS</a>
 * @see <a
 *     href="https://github.com/apache/kafka/blob/8007211cc982d8458223e866c1ee7d94b69e0249/core/src/main/scala/kafka/common/Topic.scala#L29">Topic.scala
 *     legalChars</a>
 */
public final class TopicSlug {

  // Only ASCII lowercase alphanumerics and hyphens. Periods excluded because '.' is the topic
  // segment delimiter (opencop.byod.<slug>.<uuid>). Underscores excluded to avoid Kafka's
  // period/underscore metric collision warning.
  private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9-]");
  private static final Pattern MULTI_DASH = Pattern.compile("-{2,}");
  private static final int MAX_LENGTH = 15;

  private TopicSlug() {}

  /**
   * Converts a data source name to a slug safe for Kafka topic names.
   *
   * <p>Rules: lowercase, only {@code [a-z0-9-]}, collapse runs of {@code -}, trim leading/trailing
   * {@code -}, truncate to 15 chars. No periods (used as topic segment delimiter).
   *
   * <p>Returns {@code "unnamed"} for null, blank, or names that sanitize to empty.
   */
  public static String slugify(String name) {
    if (name == null || name.isBlank()) {
      return "unnamed";
    }
    var slug = name.toLowerCase().strip();
    slug = NON_SLUG.matcher(slug).replaceAll("-");
    slug = MULTI_DASH.matcher(slug).replaceAll("-");
    // Trim leading/trailing dashes
    slug = slug.replaceAll("^-+|-+$", "");
    if (slug.length() > MAX_LENGTH) {
      slug = slug.substring(0, MAX_LENGTH);
      // Don't end on a dash after truncation
      slug = slug.replaceAll("-+$", "");
    }
    return slug.isEmpty() ? "unnamed" : slug;
  }
}
