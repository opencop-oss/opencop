// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.broker.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.model.provenance.FeatureProvenance;
import com.opencop.core.sdk.model.provenance.FeatureProvenance.FeatureSourceKind;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

class ProvenanceHeadersTest {

  private static final UUID MAPPER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440600");
  private static final Instant RECEIVED_AT = Instant.parse("2026-04-16T13:59:00Z");
  private static final Instant COMPLETED_AT = Instant.parse("2026-04-16T13:59:30.250Z");

  private static final FeatureProvenance FULL =
      new FeatureProvenance(
          FeatureSourceKind.DATA_SOURCE,
          MAPPER_ID,
          7,
          "topic:0:99",
          "topic:0:99:7",
          RECEIVED_AT,
          COMPLETED_AT);

  @Test
  void survivesARoundTrip() {
    var restored = ProvenanceHeaders.from(headersOf(ProvenanceHeaders.toHeaders(FULL)));

    assertEquals(FULL, restored);
  }

  @Test
  void absentComponentsAreOmittedRatherThanAssertedAsNull() {
    var partial =
        new FeatureProvenance(null, MAPPER_ID, null, "topic:0:99", null, RECEIVED_AT, null);

    var headers = ProvenanceHeaders.toHeaders(partial);

    assertEquals(3, headers.size());
    assertFalse(headers.containsKey("opencop-provenance-source-kind"));
    assertEquals(partial, ProvenanceHeaders.from(headersOf(headers)));
  }

  @Test
  void anIdentityExactlyAtTheStorageBoundIsCarried() {
    var atBound = "t".repeat(255);
    var provenance =
        new FeatureProvenance(null, MAPPER_ID, null, atBound, atBound, RECEIVED_AT, null);

    var restored = ProvenanceHeaders.from(headersOf(ProvenanceHeaders.toHeaders(provenance)));

    assertEquals(atBound, restored.ingressId());
    assertEquals(atBound, restored.mappingExecutionId());
  }

  @Test
  void anIdentityOverTheStorageBoundIsDroppedRatherThanTruncated() {
    var overBound = "t".repeat(256);
    var provenance =
        new FeatureProvenance(null, MAPPER_ID, null, overBound, overBound, RECEIVED_AT, null);

    var headers = ProvenanceHeaders.toHeaders(provenance);

    // geo.features stores both as varchar(255). A Kafka topic may be 249 characters, so a BYOD
    // topic can push topic:partition:offset past the column and fail the feature insert for the
    // sake of bookkeeping. Dropped, not truncated: two messages sharing a long prefix would
    // truncate to one identity and dedupe against each other.
    assertFalse(headers.containsKey("opencop-provenance-ingress-id"));
    assertFalse(headers.containsKey("opencop-provenance-mapping-execution-id"));

    var restored = ProvenanceHeaders.from(headersOf(headers));
    assertNull(restored.ingressId());
    assertNull(restored.mappingExecutionId());

    // The rest of the provenance still survives - one over-long field must not cost the others.
    assertEquals(MAPPER_ID, restored.mapperId());
    assertEquals(RECEIVED_AT, restored.sourceReceivedAt());
  }

  @Test
  void anOverBoundIdentityFromAForeignProducerIsDroppedOnRead() {
    var headers = new RecordHeaders();
    headers.add("opencop-provenance-ingress-id", "t".repeat(256).getBytes(StandardCharsets.UTF_8));
    headers.add(
        "opencop-provenance-source-received-at",
        RECEIVED_AT.toString().getBytes(StandardCharsets.UTF_8));

    var restored = ProvenanceHeaders.from(headers);

    assertNull(restored.ingressId());
    assertEquals(RECEIVED_AT, restored.sourceReceivedAt());
  }

  @Test
  void emptyProvenanceProducesNoHeaders() {
    assertTrue(ProvenanceHeaders.toHeaders(FeatureProvenance.NONE).isEmpty());
    assertTrue(ProvenanceHeaders.toHeaders(null).isEmpty());
  }

  @Test
  void absentHeadersReadAsNothingKnown() {
    assertEquals(FeatureProvenance.NONE, ProvenanceHeaders.from(new RecordHeaders()));
    assertEquals(FeatureProvenance.NONE, ProvenanceHeaders.from(null));
  }

  @Test
  void unrecognizedHeadersAreNotCarriedForward() {
    var headers = headersOf(ProvenanceHeaders.toHeaders(FULL));
    headers.add("opencop-dlt-stage", "VALIDATION".getBytes(StandardCharsets.UTF_8));
    headers.add("x-someone-elses-header", "value".getBytes(StandardCharsets.UTF_8));

    var forwarded = ProvenanceHeaders.forward(headers);

    // Reading only the names this class knows is what makes it an allowlist: a republish carries
    // provenance and nothing else a producer happened to attach.
    assertEquals(ProvenanceHeaders.toHeaders(FULL), forwarded);
  }

  @Test
  void aMalformedComponentIsDroppedWithoutLosingTheRest() {
    var headers = headersOf(ProvenanceHeaders.toHeaders(FULL));
    headers.remove("opencop-provenance-mapper-version");
    headers.add("opencop-provenance-mapper-version", "seven".getBytes(StandardCharsets.UTF_8));

    var restored = ProvenanceHeaders.from(headers);

    // Provenance is metadata: rejecting the whole record because one field was unparseable would
    // trade a real event for a bookkeeping detail.
    assertNull(restored.mapperVersion());
    assertEquals(MAPPER_ID, restored.mapperId());
    assertEquals("topic:0:99", restored.ingressId());
    assertEquals(RECEIVED_AT, restored.sourceReceivedAt());
  }

  @Test
  void anUnknownSourceKindIsDropped() {
    var headers = new RecordHeaders();
    headers.add("opencop-provenance-source-kind", "SATELLITE".getBytes(StandardCharsets.UTF_8));

    assertNull(ProvenanceHeaders.from(headers).sourceKind());
  }

  @Test
  void aBlankHeaderReadsAsAbsent() {
    var headers = new RecordHeaders();
    headers.add("opencop-provenance-ingress-id", "   ".getBytes(StandardCharsets.UTF_8));

    assertNull(ProvenanceHeaders.from(headers).ingressId());
  }

  private static Headers headersOf(Map<String, String> values) {
    var headers = new RecordHeaders();
    values.forEach((name, value) -> headers.add(name, value.getBytes(StandardCharsets.UTF_8)));
    return headers;
  }
}
