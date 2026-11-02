// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.mapping.ingress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.sdk.business.model.integration.Integration;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IngressMapperContributions;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IntegrationIngressMappers;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.model.mapper.MappedRecord;
import com.opencop.core.sdk.model.mapper.MappingRequest;
import com.opencop.core.sdk.model.mapper.MappingResponse;
import com.opencop.core.sdk.model.mapper.profile.DynamicMapper;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.PayloadIngressMapper;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.WriteOnlySourceException;
import com.opencop.core.sdk.technical.mapper.MapperFacade;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema.TestIntegration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.geojson.Feature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class EventDecoderRegistryTest {

  private static final Integration TEST_INTEGRATION =
      new TestIntegration(
          UUID.fromString("00000000-0000-4000-8000-0000000000aa"),
          "Test integration",
          "Test integration");

  private static final UUID MAPPER_ID = UUID.fromString("00000000-0000-4000-8000-000000000301");
  private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-4000-8000-000000000201");
  private static final UUID OTHER_SOURCE_ID =
      UUID.fromString("00000000-0000-4000-8000-000000000202");
  private static final UUID OBJECT_ID = UUID.fromString("00000000-0000-4000-8000-000000000101");

  private final Logger logger = (Logger) LoggerFactory.getLogger(EventDecoderRegistry.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  private MapperProfileService service;
  private MapperProfile mapper;
  private EventDecoderRegistry registry;

  @BeforeEach
  void setUp() {
    service = mock(MapperProfileService.class);
    mapper =
        MapperProfile.builder()
            .withId(MAPPER_ID)
            .withName("TEST")
            .withDescription("Test mapper")
            .withEditable(false)
            .withIntegrationId(TEST_INTEGRATION.id())
            .withVersion(1)
            .withArchived(false)
            .build();
    when(service.findLatestForSource(SOURCE_ID)).thenReturn(Optional.of(mapper));
    when(service.findLatestForSource(OTHER_SOURCE_ID)).thenReturn(Optional.of(mapper));
    when(service.findLatest(MAPPER_ID)).thenReturn(Optional.of(mapper));

    registry =
        new EventDecoderRegistry(
            mock(MapperFacade.class),
            IngressMapperContributions.of(
                List.of(
                    new IntegrationIngressMappers(
                        TEST_INTEGRATION, List.of(new ThrowingDecoder())))),
            service);

    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
    appender.stop();
  }

  @Test
  void logsFirstFailureWithContext() {
    var startedAt = Instant.now();
    assertThrows(
        RawDataParsingException.class,
        () ->
            registry.decodeAndMap(
                OBJECT_ID, new MappingProperties(SOURCE_ID, null, false), "bad".getBytes()));
    var completedAt = Instant.now();

    assertThat(appender.list).hasSize(1);

    var event = appender.list.getFirst();
    assertThat(event.getLevel()).isEqualTo(Level.WARN);
    assertThat(firstFailureAt(event)).isBetween(startedAt, completedAt);
    assertThat(event.getFormattedMessage())
        .contains("sourceId=" + SOURCE_ID)
        .contains("mapperId=" + MAPPER_ID)
        .contains("mapper='TEST'")
        .contains("firstFailureAt=")
        .contains("errorType=IllegalArgumentException")
        .contains("point: illegal or missing lat")
        .doesNotContain("\n")
        .doesNotContain("\r");
  }

  @Test
  void aSourceWithoutAnIngressMapperThatReadsIsAParsingFailure() {
    var sourceWithoutMapper = UUID.randomUUID();
    when(service.findLatestForSource(sourceWithoutMapper)).thenReturn(Optional.empty());
    when(service.onlyWrites(sourceWithoutMapper)).thenReturn(false);

    assertThrows(
        RawDataParsingException.class,
        () ->
            registry.decodeAndMapRealTimeResult(
                OBJECT_ID,
                new MappingProperties(sourceWithoutMapper, null, false),
                "bad".getBytes()));
  }

  @Test
  void aSourceThatOnlyWritesDecodesNothingItReceives() {
    var writeOnlySource = UUID.randomUUID();
    when(service.findLatestForSource(writeOnlySource)).thenReturn(Optional.empty());
    when(service.onlyWrites(writeOnlySource)).thenReturn(true);

    assertThrows(
        WriteOnlySourceException.class,
        () ->
            registry.decodeAndMapRealTimeResult(
                OBJECT_ID, new MappingProperties(writeOnlySource, null, false), "b".getBytes()));
    assertThat(appender.list).isEmpty();
  }

  private static Instant firstFailureAt(ILoggingEvent event) {
    var message = event.getFormattedMessage();
    var prefix = "firstFailureAt=";
    var start = message.indexOf(prefix) + prefix.length();
    var end = message.indexOf(", errorType=", start);
    return Instant.parse(message.substring(start, end));
  }

  @Test
  void suppressesRepeatedFailuresForSameKeyWithinWindow() {
    assertThrows(
        RawDataParsingException.class,
        () ->
            registry.decodeAndMap(
                OBJECT_ID, new MappingProperties(SOURCE_ID, null, false), "bad".getBytes()));
    assertThrows(
        RawDataParsingException.class,
        () ->
            registry.decodeAndMap(
                UUID.randomUUID(),
                new MappingProperties(SOURCE_ID, null, false),
                "bad".getBytes()));

    assertThat(appender.list).hasSize(1);
  }

  @Test
  void differentSourcesDoNotSuppressEachOther() {
    assertThrows(
        RawDataParsingException.class,
        () ->
            registry.decodeAndMap(
                OBJECT_ID, new MappingProperties(SOURCE_ID, null, false), "bad".getBytes()));
    assertThrows(
        RawDataParsingException.class,
        () ->
            registry.decodeAndMap(
                UUID.randomUUID(),
                new MappingProperties(OTHER_SOURCE_ID, null, false),
                "bad".getBytes()));

    assertThat(appender.list).hasSize(2);
    assertThat(appender.list.get(0).getFormattedMessage()).contains("sourceId=" + SOURCE_ID);
    assertThat(appender.list.get(1).getFormattedMessage()).contains("sourceId=" + OTHER_SOURCE_ID);
  }

  @Test
  void richRealTimeOverloadsWrapFailuresWhileImportApiPropagatesOriginalFailure() {
    var properties = new MappingProperties(SOURCE_ID, null, false);

    assertThrows(
        RawDataParsingException.class,
        () -> registry.decodeAndMapRealTimeResult(OBJECT_ID, properties, "bad".getBytes()));
    assertThrows(
        RawDataParsingException.class,
        () ->
            registry.decodeAndMapRealTimeResult(
                MAPPER_ID, OBJECT_ID, properties, "bad".getBytes()));
    assertThrows(
        IllegalArgumentException.class,
        () -> registry.decodeAndMapResult(MAPPER_ID, OBJECT_ID, properties, "bad".getBytes()));

    assertThat(appender.list).hasSize(1);
  }

  @Test
  void listAndRichApisDecodeOnlyOnceAndReturnTheSameFeatures() {
    var decoder = new CountingDecoder();
    var localRegistry =
        new EventDecoderRegistry(
            mock(MapperFacade.class),
            IngressMapperContributions.of(
                List.of(new IntegrationIngressMappers(TEST_INTEGRATION, List.of(decoder)))),
            service);
    var properties = new MappingProperties(SOURCE_ID, null, false);

    var rich = localRegistry.decodeAndMapRealTimeResult(OBJECT_ID, properties, "ok".getBytes());
    var legacy = localRegistry.decodeAndMap(OBJECT_ID, properties, "ok".getBytes());

    assertThat(rich.features()).hasSize(1);
    assertThat(legacy).hasSize(1);
    assertThat(decoder.invocations).hasValue(2);
  }

  @Test
  void archivedMapperResolvedBySourceIsUnavailableBeforeStaticDecoderInvocation() {
    var decoder = new CountingDecoder();
    var archivedMapper = mapper.toBuilder().withArchived(true).build();
    when(service.findLatestForSource(SOURCE_ID)).thenReturn(Optional.of(archivedMapper));
    var localRegistry =
        new EventDecoderRegistry(
            mock(MapperFacade.class),
            IngressMapperContributions.of(
                List.of(new IntegrationIngressMappers(TEST_INTEGRATION, List.of(decoder)))),
            service);

    var failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                localRegistry.decodeAndMapRealTimeResult(
                    OBJECT_ID,
                    new MappingProperties(SOURCE_ID, null, false),
                    "plugin payload".getBytes()));

    assertThat(failure).hasMessage("ingress mapper unavailable");
    assertThat(decoder.invocations).hasValue(0);
  }

  @Test
  void resolvesIngressCodeMapperByUuidAndReportsTheLocalVersion() throws Exception {
    var persistedMapper =
        mapper.toBuilder().withDescription("Persisted metadata").withVersion(41).build();
    when(service.findLatest(MAPPER_ID)).thenReturn(Optional.of(persistedMapper));
    var decoder = new LocalVersionDecoder();
    var localRegistry =
        new EventDecoderRegistry(
            mock(MapperFacade.class),
            IngressMapperContributions.of(
                List.of(new IntegrationIngressMappers(TEST_INTEGRATION, List.of(decoder)))),
            service);

    var result =
        localRegistry.decodeAndMapResult(
            MAPPER_ID,
            OBJECT_ID,
            new MappingProperties(SOURCE_ID, null, false),
            "plugin payload".getBytes());

    assertThat(result.mapperId()).isEqualTo(MAPPER_ID);
    assertThat(result.mapperVersion()).isEqualTo(7);
    assertThat(result.mapperName()).isEqualTo("TEST");
    assertThat(result.features()).hasSize(1);
  }

  @Test
  void archivedMapperResolvedByIdIsUnavailableBeforeStaticDecoderInvocation() {
    var decoder = new CountingDecoder();
    var archivedMapper = mapper.toBuilder().withArchived(true).build();
    when(service.findLatest(MAPPER_ID)).thenReturn(Optional.of(archivedMapper));
    var localRegistry =
        new EventDecoderRegistry(
            mock(MapperFacade.class),
            IngressMapperContributions.of(
                List.of(new IntegrationIngressMappers(TEST_INTEGRATION, List.of(decoder)))),
            service);

    var failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                localRegistry.decodeAndMapResult(
                    MAPPER_ID,
                    OBJECT_ID,
                    new MappingProperties(SOURCE_ID, null, false),
                    "plugin payload".getBytes()));

    assertThat(failure).hasMessage("ingress mapper unavailable");
    assertThat(decoder.invocations).hasValue(0);
  }

  @Test
  void fallsBackToTheDataAuthoredMapperWhenNoIngressCodeMapperMatches() throws Exception {
    var dynamicMapperId = UUID.fromString("00000000-0000-4000-8000-000000000302");
    var dynamicDefinition = new DynamicMapper(UUID.randomUUID(), true, null, null, List.of());
    var persistedMapper =
        MapperProfile.builder()
            .withId(dynamicMapperId)
            .withName("DATA-AUTHORED")
            .withDescription("Persisted mapping rules")
            .withEditable(true)
            .withVersion(3)
            .withDynamicMapper(dynamicDefinition)
            .withArchived(false)
            .build();
    when(service.findLatest(dynamicMapperId)).thenReturn(Optional.of(persistedMapper));
    var mappedFeature = new Feature();
    var mapperFacade = mock(MapperFacade.class);
    when(mapperFacade.map(any(MappingRequest.class)))
        .thenReturn(
            new MappingResponse(
                dynamicDefinition.id(),
                null,
                List.of(new MappedRecord(0, List.of(mappedFeature), List.of())),
                List.of(),
                List.of()));
    var localRegistry =
        new EventDecoderRegistry(
            mapperFacade,
            IngressMapperContributions.of(
                List.of(
                    new IntegrationIngressMappers(
                        TEST_INTEGRATION, List.of(new LocalVersionDecoder())))),
            service);

    var result =
        localRegistry.decodeAndMapResult(
            dynamicMapperId,
            OBJECT_ID,
            new MappingProperties(SOURCE_ID, null, false),
            "dynamic payload".getBytes());

    assertThat(result.mapperId()).isEqualTo(dynamicMapperId);
    assertThat(result.mapperVersion()).isEqualTo(3);
    assertThat(result.features()).containsExactly(mappedFeature);
    verify(mapperFacade).map(any(MappingRequest.class));
  }

  @Test
  void usesDataAuthoredRulesWhenAContributionCollidesWithTheUserOwnedMapper() throws Exception {
    var dynamicDefinition = new DynamicMapper(UUID.randomUUID(), true, null, null, List.of());
    var persistedMapper =
        MapperProfile.builder()
            .withId(MAPPER_ID)
            .withName("DATA-AUTHORED")
            .withDescription("Persisted mapping rules")
            .withEditable(true)
            .withVersion(3)
            .withDynamicMapper(dynamicDefinition)
            .withArchived(false)
            .build();
    when(service.findLatest(MAPPER_ID)).thenReturn(Optional.of(persistedMapper));
    var mappedFeature = new Feature();
    var mapperFacade = mock(MapperFacade.class);
    when(mapperFacade.map(any(MappingRequest.class)))
        .thenReturn(
            new MappingResponse(
                dynamicDefinition.id(),
                null,
                List.of(new MappedRecord(0, List.of(mappedFeature), List.of())),
                List.of(),
                List.of()));
    var contributedDecoder = new CountingDecoder();
    var localRegistry =
        new EventDecoderRegistry(
            mapperFacade,
            IngressMapperContributions.of(
                List.of(
                    new IntegrationIngressMappers(TEST_INTEGRATION, List.of(contributedDecoder)))),
            service);

    var result =
        localRegistry.decodeAndMapResult(
            MAPPER_ID,
            OBJECT_ID,
            new MappingProperties(SOURCE_ID, null, false),
            "dynamic payload".getBytes());

    assertThat(result.mapperVersion()).isEqualTo(3);
    assertThat(result.features()).containsExactly(mappedFeature);
    assertThat(contributedDecoder.invocations).hasValue(0);
    verify(mapperFacade).map(any(MappingRequest.class));
  }

  @Test
  void rejectsContributionForAMapperNoIntegrationOwnsWithoutADataAuthoredDefinition() {
    var persistedMapper =
        MapperProfile.builder()
            .withId(MAPPER_ID)
            .withName("CATALOG-OWNED")
            .withDescription("Existing mapper no integration owns")
            .withEditable(false)
            .withVersion(1)
            .withArchived(false)
            .build();
    when(service.findLatest(MAPPER_ID)).thenReturn(Optional.of(persistedMapper));
    var contributedDecoder = new CountingDecoder();
    var mapperFacade = mock(MapperFacade.class);
    var localRegistry =
        new EventDecoderRegistry(
            mapperFacade,
            IngressMapperContributions.of(
                List.of(
                    new IntegrationIngressMappers(TEST_INTEGRATION, List.of(contributedDecoder)))),
            service);

    var failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                localRegistry.decodeAndMapResult(
                    MAPPER_ID,
                    OBJECT_ID,
                    new MappingProperties(SOURCE_ID, null, false),
                    "plugin payload".getBytes()));

    assertThat(failure).hasMessage("ingress mapper has no executable definition");
    assertThat(contributedDecoder.invocations).hasValue(0);
    verifyNoInteractions(mapperFacade);
  }

  @Test
  void archivedPluginMapperCannotFallBackToItsDataAuthoredDefinition() {
    var dynamicDefinition = new DynamicMapper(UUID.randomUUID(), true, null, null, List.of());
    var archivedMapper =
        mapper.toBuilder()
            .withIntegrationId(TEST_INTEGRATION.id())
            .withDynamicMapper(dynamicDefinition)
            .withArchived(true)
            .build();
    when(service.findLatest(MAPPER_ID)).thenReturn(Optional.of(archivedMapper));
    var mapperFacade = mock(MapperFacade.class);
    var localRegistry =
        new EventDecoderRegistry(
            mapperFacade,
            IngressMapperContributions.of(
                List.of(new IntegrationIngressMappers(TEST_INTEGRATION, List.of()))),
            service);

    var failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                localRegistry.decodeAndMapResult(
                    MAPPER_ID,
                    OBJECT_ID,
                    new MappingProperties(SOURCE_ID, null, false),
                    "dynamic payload".getBytes()));

    assertThat(failure).hasMessage("ingress mapper unavailable");
    verifyNoInteractions(mapperFacade);
  }

  @Test
  void nullDecoderOutputRetainsRealTimeFailureBehavior() {
    var localRegistry =
        new EventDecoderRegistry(
            mock(MapperFacade.class),
            IngressMapperContributions.of(
                List.of(
                    new IntegrationIngressMappers(TEST_INTEGRATION, List.of(new NullDecoder())))),
            service);

    assertThrows(
        RawDataParsingException.class,
        () ->
            localRegistry.decodeAndMapRealTimeResult(
                OBJECT_ID, new MappingProperties(SOURCE_ID, null, false), "bad".getBytes()));
  }

  private static final class ThrowingDecoder implements PayloadIngressMapper<Feature> {

    @Override
    public UUID id() {
      return MAPPER_ID;
    }

    @Override
    public String name() {
      return "TEST";
    }

    @Override
    public String description() {
      return "test";
    }

    @Override
    public int version() {
      return 1;
    }

    @Override
    public List<Feature> map(UUID id, MappingProperties properties, byte[] input) {
      throw new IllegalArgumentException("Test mapper failure: point: illegal or missing lat");
    }
  }

  private static class CountingDecoder implements PayloadIngressMapper<Feature> {

    private final AtomicInteger invocations = new AtomicInteger();

    @Override
    public UUID id() {
      return MAPPER_ID;
    }

    @Override
    public String name() {
      return "TEST";
    }

    @Override
    public String description() {
      return "test";
    }

    @Override
    public int version() {
      return 1;
    }

    @Override
    public List<Feature> map(UUID id, MappingProperties properties, byte[] input) {
      invocations.incrementAndGet();
      return List.of(new Feature());
    }
  }

  private static final class NullDecoder extends CountingDecoder {

    @Override
    public List<Feature> map(UUID id, MappingProperties properties, byte[] input) {
      return null;
    }
  }

  private static final class LocalVersionDecoder extends CountingDecoder {

    @Override
    public int version() {
      return 7;
    }
  }

  private static final class ReverseDecoder extends CountingDecoder {}
}
