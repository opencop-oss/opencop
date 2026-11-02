// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.geojson.Feature;
import org.junit.jupiter.api.Test;

class ConsumerLogSupportTest {

  @Test
  void totalPayloadBytes_sumsNonNullPayloadLengths() {
    var first = new ConsumerRecord<>("topic", 0, 0L, "key-1", new byte[] {1, 2, 3});
    ConsumerRecord<String, byte[]> second = new ConsumerRecord<>("topic", 0, 1L, "key-2", null);
    var third = new ConsumerRecord<>("topic", 0, 2L, "key-3", new byte[] {4, 5});

    assertEquals(5, ConsumerLogSupport.totalPayloadBytes(List.of(first, second, third)));
  }

  @Test
  void featureIds_returnsDistinctIdsInEncounterOrder() {
    assertEquals(
        "[first, second]",
        ConsumerLogSupport.featureIds(
            List.of(feature("first"), feature("second"), feature("first"))));
  }

  @Test
  void featureIds_capRendersSampleWithRemainderAndMissingCounts() {
    var missing = new Feature();

    assertEquals(
        "[first, second] (+2 more) (1 without ids)",
        ConsumerLogSupport.featureIds(
            Arrays.asList(
                feature("first"), feature("second"), feature("third"), feature("fourth"), missing),
            2));
  }

  @Test
  void featureIds_reportsMissingIds() {
    var missing = new Feature();
    var blank = new Feature();
    blank.setId("   ");

    assertEquals(
        "[first] (3 without ids)",
        ConsumerLogSupport.featureIds(Arrays.asList(feature("first"), missing, null, blank)));
  }

  @Test
  void featureIds_returnsEmptyListWhenNoIdsArePresent() {
    assertEquals(
        "[] (2 without ids)", ConsumerLogSupport.featureIds(Arrays.asList(new Feature(), null)));
  }

  private static Feature feature(String id) {
    var feature = new Feature();
    feature.setId(id);
    return feature;
  }
}
