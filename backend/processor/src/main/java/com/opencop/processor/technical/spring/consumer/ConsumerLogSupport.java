// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.consumer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.geojson.Feature;

final class ConsumerLogSupport {

  private ConsumerLogSupport() {}

  static int totalPayloadBytes(List<ConsumerRecord<String, byte[]>> records) {
    return records.stream()
        .map(ConsumerRecord::value)
        .filter(Objects::nonNull)
        .mapToInt(value -> value.length)
        .sum();
  }

  static long elapsedMillisSince(long startedAtNanos) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
  }

  static String featureIds(List<Feature> features) {
    return featureIds(features, Integer.MAX_VALUE);
  }

  static String featureIds(List<Feature> features, int maxRendered) {
    Set<String> ids = new LinkedHashSet<>();
    int missingIds = 0;

    for (var feature : features) {
      var id = feature == null ? null : feature.getId();
      if (id == null || id.isBlank()) {
        missingIds++;
        continue;
      }
      ids.add(id);
    }

    var summary = new StringBuilder(ids.stream().limit(maxRendered).toList().toString());
    if (ids.size() > maxRendered) {
      summary.append(" (+").append(ids.size() - maxRendered).append(" more)");
    }
    if (missingIds > 0) {
      summary.append(" (").append(missingIds).append(" without ids)");
    }
    return summary.toString();
  }
}
