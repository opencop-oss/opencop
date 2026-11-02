// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.broker.kafka;

import static com.opencop.core.sdk.technical.gateway.broker.kafka.KafkaNaming.DLT_SUFFIX;
import static com.opencop.core.sdk.technical.gateway.broker.kafka.KafkaNaming.TOPIC_TEMPLATE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class KafkaNamingTest {

  /**
   * Full topic names (including DLT) must be entirely lowercase. Azure Event Hubs lowercases entity
   * names at creation time — if our generated names are already lowercase, Azure's normalization is
   * a no-op and we avoid any case mismatch between what we expect and what Azure stores.
   *
   * <p>If you're changing TOPIC_TEMPLATE, DLT_SUFFIX, or TopicSlug and this test fails, consider
   * whether your change is compatible with Azure Event Hubs naming.
   */
  @Test
  void generatedTopicNamesAreAllLowercase() {
    var names = Stream.of("NiFi Weather Feed", "MyFeed", "ALLCAPS", "Café Sensor", "test-123");
    names.forEach(
        sourceName -> {
          var slug = TopicSlug.slugify(sourceName);
          var sourceId = UUID.randomUUID();
          var topic = TOPIC_TEMPLATE.formatted(slug, sourceId);
          var dlt = topic + DLT_SUFFIX;

          assertEquals(topic, topic.toLowerCase(), "Topic must be lowercase: " + topic);
          assertEquals(dlt, dlt.toLowerCase(), "DLT topic must be lowercase: " + dlt);
        });
  }
}
