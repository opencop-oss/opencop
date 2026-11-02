// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.observability.history;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class HistoricalTraceContext {
  public static final String TRACEPARENT = "traceparent";
  public static final String TRACESTATE = "tracestate";
  public static final int TRACESTATE_MAX_LENGTH = 512;
  public static final String ATTRIBUTE_REQUEST_ID = "requestId";
  public static final String ATTRIBUTE_JOB_ID = "jobId";
  public static final String ATTRIBUTE_CHUNK_INDEX = "chunkIndex";
  public static final String ATTRIBUTE_SUBJECT_TYPE = "subjectType";
  public static final String ATTRIBUTE_SAMPLER_VERSION = "samplerVersion";
  public static final String ATTRIBUTE_PROFILE = "profile";
  public static final String ATTRIBUTE_ACTIVE_SNAPSHOT_READ_PATH_CANDIDATE =
      "history.active_snapshot.read_path_candidate";
  public static final String ATTRIBUTE_ACTIVE_SNAPSHOT_SOURCE_COUNT =
      "history.active_snapshot.source_count";
  public static final String ATTRIBUTE_ACTIVE_SNAPSHOT_CHUNK_FROM =
      "history.active_snapshot.chunk_from";
  public static final String ATTRIBUTE_ACTIVE_SNAPSHOT_CHUNK_TO =
      "history.active_snapshot.chunk_to";
  public static final String ATTRIBUTE_ACTIVE_SNAPSHOT_FETCH_SIZE =
      "history.active_snapshot.fetch_size";
  public static final String ATTRIBUTE_ACTIVE_SNAPSHOT_TIMEOUT_SECONDS =
      "history.active_snapshot.timeout_seconds";
  public static final String FORBIDDEN_ATTRIBUTE_LATITUDE = "latitude";
  public static final String FORBIDDEN_ATTRIBUTE_LONGITUDE = "longitude";
  public static final String FORBIDDEN_ATTRIBUTE_OBJECT_ID = "objectId";
  public static final String FORBIDDEN_ATTRIBUTE_REQUESTED_BY = "requestedBy";
  public static final String FORBIDDEN_ATTRIBUTE_USER_ID = "userId";

  private static final Pattern TRACEPARENT_PATTERN =
      Pattern.compile("^[\\da-f]{2}-[\\da-f]{32}-[\\da-f]{16}-[\\da-f]{2}$");
  private static final Set<String> SPAN_ALLOWLIST =
      Set.of(
          ATTRIBUTE_REQUEST_ID,
          ATTRIBUTE_JOB_ID,
          ATTRIBUTE_CHUNK_INDEX,
          ATTRIBUTE_SUBJECT_TYPE,
          ATTRIBUTE_SAMPLER_VERSION,
          ATTRIBUTE_PROFILE,
          ATTRIBUTE_ACTIVE_SNAPSHOT_READ_PATH_CANDIDATE,
          ATTRIBUTE_ACTIVE_SNAPSHOT_SOURCE_COUNT,
          ATTRIBUTE_ACTIVE_SNAPSHOT_CHUNK_FROM,
          ATTRIBUTE_ACTIVE_SNAPSHOT_CHUNK_TO,
          ATTRIBUTE_ACTIVE_SNAPSHOT_FETCH_SIZE,
          ATTRIBUTE_ACTIVE_SNAPSHOT_TIMEOUT_SECONDS);
  private static final Set<String> FORBIDDEN_SPAN_KEYS =
      Set.of(
          FORBIDDEN_ATTRIBUTE_LATITUDE,
          FORBIDDEN_ATTRIBUTE_LONGITUDE,
          FORBIDDEN_ATTRIBUTE_OBJECT_ID,
          FORBIDDEN_ATTRIBUTE_REQUESTED_BY,
          FORBIDDEN_ATTRIBUTE_USER_ID);

  private static final TextMapSetter<Map<String, String>> MAP_SETTER =
      (carrier, key, value) -> {
        if (carrier != null && key != null && value != null) {
          carrier.put(key, value);
        }
      };

  private static final TextMapGetter<Map<String, String>> MAP_GETTER =
      new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
          return carrier == null ? List.of() : carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
          return carrier == null ? null : carrier.get(key);
        }
      };

  private HistoricalTraceContext() {}

  public static Map<String, String> sanitize(Map<String, String> input) {
    if (input == null || input.isEmpty()) {
      return Map.of();
    }
    String traceparent = input.get(TRACEPARENT);
    if (!isValidTraceparent(traceparent)) {
      return Map.of();
    }
    String tracestate = input.get(TRACESTATE);
    if (tracestate != null && tracestate.length() > TRACESTATE_MAX_LENGTH) {
      return Map.of();
    }
    Map<String, String> sanitized = new LinkedHashMap<>();
    sanitized.put(TRACEPARENT, traceparent);
    if (tracestate != null && !tracestate.isBlank()) {
      sanitized.put(TRACESTATE, tracestate);
    }
    return sanitized;
  }

  public static Map<String, String> captureCurrent() {
    Map<String, String> carrier = new LinkedHashMap<>();
    W3CTraceContextPropagator.getInstance().inject(Context.current(), carrier, MAP_SETTER);
    return sanitize(carrier);
  }

  public static Context parentContext(Map<String, String> carrier) {
    Map<String, String> sanitized = sanitize(carrier);
    if (sanitized.isEmpty()) {
      return Context.root();
    }
    return W3CTraceContextPropagator.getInstance().extract(Context.root(), sanitized, MAP_GETTER);
  }

  public static Map<String, String> empty() {
    return Map.of();
  }

  public static Attributes spanAttributes(Map<String, String> input) {
    if (input == null || input.isEmpty()) {
      return Attributes.empty();
    }
    for (String key : input.keySet()) {
      if (FORBIDDEN_SPAN_KEYS.contains(key)) {
        throw new IllegalArgumentException("forbidden span attribute key: " + key);
      }
    }
    AttributesBuilder builder = Attributes.builder();
    for (Map.Entry<String, String> entry : input.entrySet()) {
      String key = entry.getKey();
      if (!SPAN_ALLOWLIST.contains(key)) {
        continue;
      }
      String value = entry.getValue();
      if (value != null && !value.isBlank()) {
        builder.put(key, value);
      }
    }
    return builder.build();
  }

  private static boolean isValidTraceparent(String traceparent) {
    return traceparent != null && TRACEPARENT_PATTERN.matcher(traceparent).matches();
  }
}
