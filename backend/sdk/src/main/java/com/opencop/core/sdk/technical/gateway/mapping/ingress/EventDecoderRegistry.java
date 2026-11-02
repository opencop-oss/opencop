// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.mapping.ingress;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IngressMapperContributions;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.policy.integration.mapping.ingress.IngressMapperAvailabilityPolicy;
import com.opencop.core.sdk.business.policy.integration.mapping.ingress.IngressMapperCatalogOwnershipPolicy;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.DecodedMappingResult;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.PayloadIngressMapper;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.WriteOnlySourceException;
import com.opencop.core.sdk.technical.gateway.mapping.ingress.dynamic.DynamicIngressMapperV1;
import com.opencop.core.sdk.technical.mapper.MapperFacade;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.geojson.Feature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EventDecoderRegistry {

  private static final Logger log = LoggerFactory.getLogger(EventDecoderRegistry.class);
  private static final int MAX_ERROR_MESSAGE_LENGTH = 200;
  private static final String MAPPER_NOT_FOUND = "Mapper not found for objectId={}, sourceId={}";
  private static final String MAPPER_UNAVAILABLE = "ingress mapper unavailable";
  private static final String MAPPER_WITHOUT_EXECUTABLE_DEFINITION =
      "ingress mapper has no executable definition";
  private final MapperFacade mapperFacade;
  private final MapperProfileService mappers;
  private final RealTimeFailureDebouncer realTimeFailureDebouncer;

  private record DecoderKey(UUID mapperId, int version) {}

  // Contribution-backed code decoders (e.g. CoT); keyed by decoder ID; never evicted.
  private final Map<UUID, PayloadIngressMapper<? extends Feature>> staticDecoders;
  // JSON-rule-backed dynamic decoders; keyed by (mapperId, version); evicted by Caffeine.
  private final Cache<DecoderKey, PayloadIngressMapper<Feature>> dynamicDecoders;

  EventDecoderRegistry(
      MapperFacade mapperFacade,
      IngressMapperContributions contributions,
      MapperProfileService mappers) {
    var statics = new ConcurrentHashMap<UUID, PayloadIngressMapper<? extends Feature>>();
    for (PayloadIngressMapper<? extends Feature> decoder : contributions.mappers()) {
      var decoderId = decoder.id();
      var existing = statics.putIfAbsent(decoderId, decoder);
      if (existing != null) {
        var implementations =
            List.of(existing.getClass().getName(), decoder.getClass().getName()).stream()
                .sorted()
                .toList();
        throw new IllegalStateException(
            "Duplicate ingress mapper ID %s: %s"
                .formatted(decoderId, String.join(", ", implementations)));
      }
    }
    this.staticDecoders = statics;
    this.dynamicDecoders =
        Caffeine.newBuilder().maximumSize(256).expireAfterAccess(15, TimeUnit.MINUTES).build();
    this.mapperFacade = mapperFacade;
    this.mappers = mappers;
    this.realTimeFailureDebouncer = RealTimeFailureDebouncer.withDefaults();
  }

  public List<Feature> decodeAndMap(UUID objectId, MappingProperties properties, byte[] raw) {
    var result = decodeAndMapRealTimeResult(objectId, properties, raw);
    result.throwIfErrorDiagnostic();
    return result.features();
  }

  public DecodedMappingResult decodeAndMapRealTimeResult(
      UUID objectId, MappingProperties properties, byte[] raw) {
    var sourceId = properties.sourceId();
    var mapper =
        mappers.findLatestForSource(sourceId).orElseThrow(() -> noSourceMapper(objectId, sourceId));
    return this.decodeAndMapRealTimeResult(mapper, objectId, properties, raw);
  }

  public List<Feature> decodeAndMap(
      UUID mapperId, UUID objectId, MappingProperties properties, byte[] raw) {
    var result = decodeAndMapRealTimeResult(mapperId, objectId, properties, raw);
    result.throwIfErrorDiagnostic();
    return result.features();
  }

  public DecodedMappingResult decodeAndMapRealTimeResult(
      UUID mapperId, UUID objectId, MappingProperties properties, byte[] raw) {
    var mapper =
        mappers.findLatest(mapperId).orElseThrow(() -> mapperNotFound(objectId, properties));
    return this.decodeAndMapRealTimeResult(mapper, objectId, properties, raw);
  }

  public DecodedMappingResult decodeAndMapResult(
      UUID mapperId, UUID objectId, MappingProperties properties, byte[] raw) throws Exception {
    var mapper =
        mappers.findLatest(mapperId).orElseThrow(() -> mapperNotFound(objectId, properties));
    requireAvailable(mapper);
    return resolveDecoder(mapper).mapResult(objectId, properties, raw);
  }

  private DecodedMappingResult decodeAndMapRealTimeResult(
      MapperProfile mapper, UUID objectId, MappingProperties properties, byte[] raw) {
    requireAvailable(mapper);
    var decoder = resolveDecoder(mapper);
    try {
      return decoder.mapResult(objectId, properties, raw);
    } catch (Exception exception) {
      var firstFailureAt = firstFailureAt(properties.sourceId(), mapper.id());
      if (firstFailureAt != null) {
        log.warn(
            "Real-time decode/map failed for sourceId={}, mapperId={}, mapper='{}', firstFailureAt={}, errorType={}, errorMessage={}",
            properties.sourceId(),
            mapper.id(),
            mapper.name(),
            firstFailureAt,
            exception.getClass().getSimpleName(),
            summarizeErrorMessage(exception));
      }
      throw new RawDataParsingException(objectId.toString(), exception);
    }
  }

  private PayloadIngressMapper<? extends Feature> resolveDecoder(MapperProfile mapper) {
    var s = staticDecoders.get(mapper.id());
    if (s != null && IngressMapperCatalogOwnershipPolicy.canUseContribution(mapper)) {
      return s;
    }
    if (mapper.dynamicMapper() == null) {
      throw new IllegalStateException(MAPPER_WITHOUT_EXECUTABLE_DEFINITION);
    }
    return dynamicDecoders.get(
        new DecoderKey(mapper.id(), mapper.version()),
        k -> new DynamicIngressMapperV1(mapper, mapperFacade));
  }

  /** A source that only writes has no ingress mapper by design; any other source lacks one. */
  private RuntimeException noSourceMapper(UUID objectId, UUID sourceId) {
    if (mappers.onlyWrites(sourceId)) {
      return new WriteOnlySourceException(sourceId);
    }
    return mapperNotFound(objectId, sourceId);
  }

  private static RawDataParsingException mapperNotFound(UUID objectId, Object source) {
    log.error(MAPPER_NOT_FOUND, objectId, source);
    return new RawDataParsingException(objectId.toString());
  }

  private static void requireAvailable(MapperProfile mapper) {
    if (!IngressMapperAvailabilityPolicy.isAvailable(mapper)) {
      throw new IllegalStateException(MAPPER_UNAVAILABLE);
    }
  }

  private Instant firstFailureAt(UUID sourceId, UUID mapperId) {
    return sourceId != null && mapperId != null
        ? realTimeFailureDebouncer.firstFailureAt(sourceId, mapperId)
        : Instant.now();
  }

  private static String summarizeErrorMessage(Exception e) {
    var message =
        Objects.toString(e.getMessage(), "(no message)").replace('\r', ' ').replace('\n', ' ');
    return message.length() <= MAX_ERROR_MESSAGE_LENGTH
        ? message
        : message.substring(0, MAX_ERROR_MESSAGE_LENGTH - 3) + "...";
  }
}
