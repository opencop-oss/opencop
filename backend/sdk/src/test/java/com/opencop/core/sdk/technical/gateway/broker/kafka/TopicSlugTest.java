// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.broker.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;

class TopicSlugTest {

  @ParameterizedTest(name = "\"{0}\" → \"{1}\"")
  @MethodSource("slugifyCases")
  void slugify(String input, String expected) {
    assertEquals(expected, TopicSlug.slugify(input));
  }

  @ParameterizedTest(name = "null → \"unnamed\"")
  @NullSource
  void slugify_null(String input) {
    assertEquals("unnamed", TopicSlug.slugify(input));
  }

  static Stream<Arguments> slugifyCases() {
    return Stream.of(
        // Normal names
        Arguments.of("NiFi Weather Feed", "nifi-weather-fe"),
        Arguments.of("my-feed", "my-feed"),
        Arguments.of("sensor42", "sensor42"),

        // Special characters replaced with dash
        Arguments.of("hello@world!", "hello-world"),
        Arguments.of("a/b/c", "a-b-c"),

        // Periods replaced with dash — '.' is the topic segment delimiter
        // (opencop.byod.<slug>.<uuid>), so slug must not contain periods
        Arguments.of("NiFi.Weather.Feed", "nifi-weather-fe"),
        Arguments.of("v2.1.0", "v2-1-0"),

        // Runs of dashes collapsed
        Arguments.of("a---b", "a-b"),
        Arguments.of("a...b", "a-b"),

        // Leading/trailing non-slug chars trimmed
        Arguments.of("---test---", "test"),
        Arguments.of("  test  ", "test"),
        Arguments.of("!!cool!!", "cool"),

        // Truncation at 15 chars
        Arguments.of("abcdefghijklmnopqrs", "abcdefghijklmno"),
        Arguments.of("this-is-a-very-long-name", "this-is-a-very"),

        // Truncation doesn't end on dash
        Arguments.of("abcdefghijklmno-pqr", "abcdefghijklmno"),
        Arguments.of("abcdefghijklmn-pqr", "abcdefghijklmn"),

        // Edge cases → "unnamed"
        Arguments.of("", "unnamed"),
        Arguments.of("   ", "unnamed"),
        Arguments.of("...", "unnamed"),
        Arguments.of("---", "unnamed"),
        Arguments.of("!!!@@@", "unnamed"),

        // Mixed case
        Arguments.of("MyFeed", "myfeed"),
        Arguments.of("ALLCAPS", "allcaps"),

        // Already valid short slug
        Arguments.of("ok", "ok"),

        // Digits
        Arguments.of("feed-123", "feed-123"),
        Arguments.of("123", "123"),

        // Non-ASCII — Kafka only allows [a-zA-Z0-9._-] in topic names.
        // Non-ASCII chars are stripped; UUID suffix guarantees uniqueness.
        // See: Topic.java LEGAL_CHARS, Topic.scala legalChars
        Arguments.of("你好", "unnamed"),
        Arguments.of("test-你好-bar", "test-bar"),
        Arguments.of("données", "donn-es"),
        Arguments.of("café-feed", "caf-feed"),
        Arguments.of("🚀rocket", "rocket"));
  }

  /** Even when non-ASCII names produce identical slugs, the UUID suffix keeps topics unique. */
  @Test
  void identicalSlugsProduceUniqueTopicNames() {
    // Both pure-Chinese names degrade to "unnamed" — same slug
    var slug1 = TopicSlug.slugify("你好");
    var slug2 = TopicSlug.slugify("こんにちは");
    assertEquals(slug1, slug2, "Both should degrade to 'unnamed'");

    // But full topic names differ because of the UUID suffix
    var topic1 = KafkaNaming.TOPIC_TEMPLATE.formatted(slug1, UUID.randomUUID());
    var topic2 = KafkaNaming.TOPIC_TEMPLATE.formatted(slug2, UUID.randomUUID());
    assertNotEquals(topic1, topic2, "UUID suffix guarantees uniqueness despite identical slugs");
  }
}
