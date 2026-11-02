// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.broker.kafka;

import com.opencop.core.sdk.model.provenance.FeatureProvenance;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.common.header.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Carries {@link FeatureProvenance} across topics as Kafka headers, and is the only place that
 * knows their names.
 *
 * <p>Headers rather than payload fields deliberately: provenance is internal pipeline bookkeeping,
 * and the feature payload is the public GeoJSON contract that clients read. Putting it in headers
 * keeps it off that contract entirely.
 *
 * <p>{@link #from(Headers)} reads only the names below, which is what makes it an allowlist: a
 * republish carries forward exactly the provenance this class recognizes and silently drops
 * anything else a producer may have attached. Malformed and over-long values are dropped per-field
 * rather than failing the record — provenance is metadata, and rejecting a feature because its
 * mapper version was unparseable, or because a long Kafka topic name pushed its ingress identity
 * past the column width, would trade a real event for a bookkeeping detail.
 */
public final class ProvenanceHeaders {

  private static final Logger log = LoggerFactory.getLogger(ProvenanceHeaders.class);

  private static final String SOURCE_KIND = "opencop-provenance-source-kind";
  private static final String MAPPER_ID = "opencop-provenance-mapper-id";
  private static final String MAPPER_VERSION = "opencop-provenance-mapper-version";
  private static final String INGRESS_ID = "opencop-provenance-ingress-id";
  private static final String MAPPING_EXECUTION_ID = "opencop-provenance-mapping-execution-id";
  private static final String SOURCE_RECEIVED_AT = "opencop-provenance-source-received-at";
  private static final String MAPPING_COMPLETED_AT = "opencop-provenance-mapping-completed-at";

  /**
   * Storage bound for the two producer-influenced identities, matching {@code
   * geo.features.ingress_id} and {@code geo.features.mapping_execution_id}.
   *
   * <p>An over-long value is dropped rather than truncated. Truncation would be worse than absence:
   * two distinct messages sharing a long prefix would truncate to the same identity and dedupe
   * against each other, silently discarding activity. Dropping loses the same bookkeeping without
   * inventing a collision.
   */
  private static final int MAX_IDENTITY_LENGTH = 255;

  private ProvenanceHeaders() {}

  /** Header form for a producer, omitting absent components so nothing is asserted as null. */
  public static Map<String, String> toHeaders(FeatureProvenance provenance) {
    if (provenance == null || provenance.isEmpty()) {
      return Map.of();
    }
    var headers = new HashMap<String, String>();
    putIfPresent(headers, SOURCE_KIND, provenance.sourceKind());
    putIfPresent(headers, MAPPER_ID, provenance.mapperId());
    putIfPresent(headers, MAPPER_VERSION, provenance.mapperVersion());
    putIfPresent(headers, INGRESS_ID, bounded(provenance.ingressId(), INGRESS_ID));
    putIfPresent(
        headers,
        MAPPING_EXECUTION_ID,
        bounded(provenance.mappingExecutionId(), MAPPING_EXECUTION_ID));
    putIfPresent(headers, SOURCE_RECEIVED_AT, provenance.sourceReceivedAt());
    putIfPresent(headers, MAPPING_COMPLETED_AT, provenance.mappingCompletedAt());
    return Map.copyOf(headers);
  }

  /** Reads the allowlisted provenance headers off a consumed record. */
  public static FeatureProvenance from(Headers headers) {
    if (headers == null) {
      return FeatureProvenance.NONE;
    }
    return new FeatureProvenance(
        sourceKind(value(headers, SOURCE_KIND)),
        uuid(value(headers, MAPPER_ID), MAPPER_ID),
        integer(value(headers, MAPPER_VERSION), MAPPER_VERSION),
        bounded(value(headers, INGRESS_ID), INGRESS_ID),
        bounded(value(headers, MAPPING_EXECUTION_ID), MAPPING_EXECUTION_ID),
        instant(value(headers, SOURCE_RECEIVED_AT), SOURCE_RECEIVED_AT),
        instant(value(headers, MAPPING_COMPLETED_AT), MAPPING_COMPLETED_AT));
  }

  /** Re-emits consumed provenance for a republish, so a downstream stage still sees it. */
  public static Map<String, String> forward(Headers headers) {
    return toHeaders(from(headers));
  }

  private static void putIfPresent(Map<String, String> headers, String name, Object value) {
    if (value != null) {
      headers.put(name, value.toString());
    }
  }

  /** Drops an identity that would not fit storage, on both the producer and consumer side. */
  private static String bounded(String raw, String name) {
    if (raw == null || raw.length() <= MAX_IDENTITY_LENGTH) {
      return raw;
    }
    log.debug("Dropping over-long provenance header {} ({} characters)", name, raw.length());
    return null;
  }

  private static String value(Headers headers, String name) {
    var header = headers.lastHeader(name);
    if (header == null || header.value() == null) {
      return null;
    }
    var text = new String(header.value(), StandardCharsets.UTF_8);
    return text.isBlank() ? null : text;
  }

  private static FeatureProvenance.FeatureSourceKind sourceKind(String raw) {
    if (raw == null) {
      return null;
    }
    try {
      return FeatureProvenance.FeatureSourceKind.valueOf(raw);
    } catch (IllegalArgumentException _) {
      log.debug("Dropping unrecognized provenance source kind: {}", raw);
      return null;
    }
  }

  private static UUID uuid(String raw, String name) {
    if (raw == null) {
      return null;
    }
    try {
      return UUID.fromString(raw);
    } catch (IllegalArgumentException _) {
      log.debug("Dropping malformed provenance header {}", name);
      return null;
    }
  }

  private static Integer integer(String raw, String name) {
    if (raw == null) {
      return null;
    }
    try {
      return Integer.valueOf(raw);
    } catch (NumberFormatException _) {
      log.debug("Dropping malformed provenance header {}", name);
      return null;
    }
  }

  private static Instant instant(String raw, String name) {
    if (raw == null) {
      return null;
    }
    try {
      return Instant.parse(raw);
    } catch (DateTimeParseException _) {
      log.debug("Dropping malformed provenance header {}", name);
      return null;
    }
  }
}
