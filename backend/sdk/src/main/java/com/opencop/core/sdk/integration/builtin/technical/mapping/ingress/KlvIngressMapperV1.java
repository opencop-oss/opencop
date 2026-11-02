// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.technical.mapping.ingress;

import com.opencop.core.sdk.integration.builtin.business.model.NativeMappers;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.ObjectIdStability;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.PayloadIngressMapper;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.Timestampz;
import com.opencop.core.sdk.technical.model.opencop.elements.ElementTypes;
import com.opencop.core.sdk.technical.model.opencop.elements.PointElement;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.geojson.Feature;
import org.geojson.Point;

public class KlvIngressMapperV1 implements PayloadIngressMapper<Feature> {

  public static final UUID MAPPER_ID = NativeMappers.KLV_INGRESS;
  public static final String MAPPER_NAME = "KLV Metadata";
  public static final String DESCRIPTION =
      "Decodes MISB ST 0601 KLV metadata into OpenCOP PLI features for RTSP sources.";

  // Keep the last position in Redis for 30 days so the object persists on the map even when the
  // data source goes offline. Online/offline status is communicated separately via attributes.
  private static final long REDIS_TTL_MS = Duration.ofDays(30).toMillis();

  @Override
  public UUID id() {
    return MAPPER_ID;
  }

  @Override
  public String name() {
    return MAPPER_NAME;
  }

  @Override
  public String description() {
    return DESCRIPTION;
  }

  @Override
  public int version() {
    return 1;
  }

  @Override
  public ObjectIdStability objectIdStability() {
    // KLV derives object ID deterministically from source-scoped MIIS, tail number, then source ID.
    return ObjectIdStability.STABLE_PER_ENTITY;
  }

  @Override
  public List<Feature> map(UUID objectId, MappingProperties properties, byte[] input) {
    var parsed = KlvTelemetryParser.parse(input);
    UUID effectiveObjectId = resolveObjectId(properties.sourceId(), parsed);

    // Sensor position (UAV body) preferred over frame center (where camera is pointing)
    Double lat = firstNonNull(parsed.sensorLat(), parsed.frameCenterLat());
    Double lon = firstNonNull(parsed.sensorLon(), parsed.frameCenterLon());

    if (lat == null || lon == null) {
      return List.of();
    }

    Point point =
        parsed.sensorAltM() != null
            ? new Point(lon, lat, parsed.sensorAltM())
            : new Point(lon, lat);

    long nowMs = System.currentTimeMillis();
    long eventMs = parsed.timestampMicros() != null ? parsed.timestampMicros() / 1000 : nowMs;

    var element =
        new PointElement.Builder()
            .symbol("130310000011120000000000000000")
            .type(ElementTypes.SYMBOL)
            .build();

    OpenCopProperties ocProps =
        new OpenCopProperties.Builder()
            .version("0.0.1")
            .id(UUID.randomUUID())
            .objectId(effectiveObjectId.toString())
            .kind("rtsp.klv.v1")
            .source(properties.sourceId())
            .displayName(resolveDisplayName(parsed))
            .timestampz(new Timestampz(eventMs, eventMs, eventMs + REDIS_TTL_MS))
            .element(element)
            .build();

    return List.of(
        OpenCopPropertiesParser.writeToFeature(ocProps, point, effectiveObjectId.toString()));
  }

  // Priority: MIIS platform UUID → tail number → source-derived fallback.
  // Combining with sourceId scopes each identifier to its originating stream.
  // NOTE: if a KLV tag is intermittently absent (tag dropout), the objectId will differ between
  // frames, creating an orphaned Redis entry under the old key (TTL = REDIS_TTL_MS). Well-behaved
  // MISB streams include identity tags in every frame; treat repeated dropouts as a stream defect.
  // Priority: call sign → platform designation → tail number → mission ID → null (frontend
  // fallback)
  private static String resolveDisplayName(KlvTelemetryParser.ParsedLocalSet parsed) {
    if (parsed.platformCallSign() != null) return parsed.platformCallSign();
    if (parsed.platformDesignation() != null) return parsed.platformDesignation();
    if (parsed.platformTailNumber() != null) return parsed.platformTailNumber();
    return parsed.missionId();
  }

  private static UUID resolveObjectId(UUID sourceId, KlvTelemetryParser.ParsedLocalSet parsed) {
    if (parsed.miisPlatformUUID() != null) {
      return UUID.nameUUIDFromBytes(
          (sourceId + ":" + parsed.miisPlatformUUID()).getBytes(StandardCharsets.UTF_8));
    }
    if (parsed.platformTailNumber() != null) {
      return UUID.nameUUIDFromBytes(
          (sourceId + ":" + parsed.platformTailNumber()).getBytes(StandardCharsets.UTF_8));
    }
    return UUID.nameUUIDFromBytes(sourceId.toString().getBytes(StandardCharsets.UTF_8));
  }

  private static <T> T firstNonNull(T a, T b) {
    return a != null ? a : b;
  }
}
