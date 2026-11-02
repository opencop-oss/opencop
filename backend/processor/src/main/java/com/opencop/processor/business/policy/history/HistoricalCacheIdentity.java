// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.policy.history;

import com.opencop.core.sdk.model.history.HistoricalFilterRequest;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class HistoricalCacheIdentity {

  /**
   * Version of the cached chunk content, part of every payload and chunk-state key. Change it
   * whenever a chunk built by the previous release must not be served, so those keys are left to
   * expire rather than reused.
   */
  public static final String SAMPLING_VERSION = "v2_latest_in_bucket";

  private final ObjectMapper objectMapper;

  public HistoricalCacheIdentity(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String payloadKey(HistoricalSubjectExecutionMeta laneMetadata, Instant chunkFrom) {
    return "opencop:history:payload:%s:%s:%s:%s:%s:%s:%s"
        .formatted(
            laneMetadata.subjectType().name(),
            laneMetadata.subjectId(),
            laneMetadata.resolution().name(),
            HistoricalChunkPlan.PLANNING_VERSION,
            SAMPLING_VERSION,
            filtersFingerprint(laneMetadata.filters()),
            chunkFrom);
  }

  public String payloadPartKey(String payloadKey, int partIndex) {
    return partIndex == 0 ? payloadKey : payloadKey + ":part:" + partIndex;
  }

  public String chunkStateKey(HistoricalSubjectExecutionMeta laneMetadata, Instant chunkFrom) {
    return "opencop:history:chunk-state:%s:%s:%s:%s:%s:%s:%s"
        .formatted(
            laneMetadata.subjectType().name(),
            laneMetadata.subjectId(),
            laneMetadata.resolution().name(),
            HistoricalChunkPlan.PLANNING_VERSION,
            SAMPLING_VERSION,
            filtersFingerprint(laneMetadata.filters()),
            chunkFrom);
  }

  String filtersFingerprint(List<HistoricalFilterRequest> filters) {
    try {
      byte[] canonical =
          objectMapper.writeValueAsBytes(
              filters == null
                  ? List.of()
                  : filters.stream()
                      .map(this::canonicalize)
                      .sorted(Comparator.comparing(HistoricalFilterCanonicalForm::type))
                      .toList());
      return sha256Hex(canonical);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to build historical cache filter fingerprint", e);
    }
  }

  private HistoricalFilterCanonicalForm canonicalize(HistoricalFilterRequest filter) {
    return new HistoricalFilterCanonicalForm(
        filter.type(), normalize(filter.params() == null ? Map.of() : filter.params()));
  }

  private Object normalize(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Map<?, ?> map) {
      TreeMap<String, Object> normalized = new TreeMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        normalized.put(String.valueOf(entry.getKey()), normalize(entry.getValue()));
      }
      return normalized;
    }
    if (value instanceof List<?> list) {
      return list.stream().map(this::normalize).toList();
    }
    return value;
  }

  private static String sha256Hex(byte[] content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(content));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }

  private record HistoricalFilterCanonicalForm(String type, Object params) {
    private HistoricalFilterCanonicalForm {
      type =
          type == null
              ? ""
              : new String(type.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }
  }
}
