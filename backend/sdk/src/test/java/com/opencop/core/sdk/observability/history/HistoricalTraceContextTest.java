// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.observability.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.Context;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HistoricalTraceContextTest {

  @Test
  void sanitize_preservesValidW3cTraceHeaders() {
    Map<String, String> input = new LinkedHashMap<>();
    input.put("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
    input.put("tracestate", "rojo=00f067aa0ba902b7");

    Map<String, String> sanitized = HistoricalTraceContext.sanitize(input);

    assertEquals(2, sanitized.size());
    assertEquals(
        "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", sanitized.get("traceparent"));
    assertEquals("rojo=00f067aa0ba902b7", sanitized.get("tracestate"));
  }

  @Test
  void sanitize_dropsInvalidTraceparentDisallowedKeyAndOversizedTracestate() {
    Map<String, String> input = new LinkedHashMap<>();
    input.put("traceparent", "bad");
    input.put("x-user-id", "550e8400-e29b-41d4-a716-446655440120");
    input.put("tracestate", "a".repeat(513));

    Map<String, String> sanitized = HistoricalTraceContext.sanitize(input);

    assertTrue(sanitized.isEmpty());
  }

  @Test
  void spanAttributes_returnsOnlyAllowlistedFields() {
    Map<String, String> input = new LinkedHashMap<>();
    input.put("requestId", "req-123");
    input.put("jobId", "job-123");
    input.put("chunkIndex", "4");
    input.put("subjectType", "SOURCE");
    input.put("samplerVersion", "v1");
    input.put("profile", "default");
    input.put("notAllowlisted", "dropped");

    var attributes = HistoricalTraceContext.spanAttributes(input);

    assertEquals(6, attributes.size());
    assertEquals("req-123", attributes.get(AttributeKey.stringKey("requestId")));
    assertEquals("job-123", attributes.get(AttributeKey.stringKey("jobId")));
    assertEquals("4", attributes.get(AttributeKey.stringKey("chunkIndex")));
    assertEquals("SOURCE", attributes.get(AttributeKey.stringKey("subjectType")));
    assertEquals("v1", attributes.get(AttributeKey.stringKey("samplerVersion")));
    assertEquals("default", attributes.get(AttributeKey.stringKey("profile")));
  }

  @Test
  void spanAttributes_includesActiveSnapshotReadShapeFields() {
    Map<String, String> input = new LinkedHashMap<>();
    input.put(
        HistoricalTraceContext.ATTRIBUTE_ACTIVE_SNAPSHOT_READ_PATH_CANDIDATE, "active_helper");
    input.put(HistoricalTraceContext.ATTRIBUTE_ACTIVE_SNAPSHOT_SOURCE_COUNT, "3");
    input.put(HistoricalTraceContext.ATTRIBUTE_ACTIVE_SNAPSHOT_CHUNK_FROM, "2026-06-30T10:00:00Z");
    input.put(HistoricalTraceContext.ATTRIBUTE_ACTIVE_SNAPSHOT_CHUNK_TO, "2026-06-30T10:05:00Z");
    input.put(HistoricalTraceContext.ATTRIBUTE_ACTIVE_SNAPSHOT_FETCH_SIZE, "5000");
    input.put(HistoricalTraceContext.ATTRIBUTE_ACTIVE_SNAPSHOT_TIMEOUT_SECONDS, "300");

    var attributes = HistoricalTraceContext.spanAttributes(input);

    assertEquals(6, attributes.size());
    assertEquals(
        "active_helper",
        attributes.get(
            AttributeKey.stringKey(
                HistoricalTraceContext.ATTRIBUTE_ACTIVE_SNAPSHOT_READ_PATH_CANDIDATE)));
    assertEquals(
        "3",
        attributes.get(
            AttributeKey.stringKey(HistoricalTraceContext.ATTRIBUTE_ACTIVE_SNAPSHOT_SOURCE_COUNT)));
    assertEquals(
        "2026-06-30T10:00:00Z",
        attributes.get(
            AttributeKey.stringKey(HistoricalTraceContext.ATTRIBUTE_ACTIVE_SNAPSHOT_CHUNK_FROM)));
    assertEquals(
        "2026-06-30T10:05:00Z",
        attributes.get(
            AttributeKey.stringKey(HistoricalTraceContext.ATTRIBUTE_ACTIVE_SNAPSHOT_CHUNK_TO)));
    assertEquals(
        "5000",
        attributes.get(
            AttributeKey.stringKey(HistoricalTraceContext.ATTRIBUTE_ACTIVE_SNAPSHOT_FETCH_SIZE)));
    assertEquals(
        "300",
        attributes.get(
            AttributeKey.stringKey(
                HistoricalTraceContext.ATTRIBUTE_ACTIVE_SNAPSHOT_TIMEOUT_SECONDS)));
  }

  @Test
  void spanAttributes_rejectsForbiddenSensitiveFields() {
    Map<String, String> input = new LinkedHashMap<>();
    input.put("requestedBy", "550e8400-e29b-41d4-a716-446655440120");
    input.put("latitude", "39.9526");
    input.put("longitude", "-75.1652");
    input.put("objectId", "secret-object");
    input.put("userId", "550e8400-e29b-41d4-a716-446655440121");

    assertThrows(
        IllegalArgumentException.class, () -> HistoricalTraceContext.spanAttributes(input));
  }

  @Test
  void parentContext_returnsRootWhenInputIsNull() {
    Context context = HistoricalTraceContext.parentContext(null);

    assertEquals(Context.root(), context);
  }

  @Test
  void captureCurrentAndEmpty_returnAllowlistedKeysOnly() {
    Map<String, String> empty = HistoricalTraceContext.empty();
    Map<String, String> captured = HistoricalTraceContext.captureCurrent();

    assertTrue(empty.isEmpty());
    assertFalse(captured.containsKey("x-user-id"));
    assertTrue(
        captured.keySet().stream()
            .allMatch(k -> k.equals("traceparent") || k.equals("tracestate")));
  }
}
