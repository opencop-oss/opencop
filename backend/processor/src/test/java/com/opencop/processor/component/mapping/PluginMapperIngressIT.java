// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.mapping;

import static com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders.CONTENT_KIND;
import static com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders.IMPORT_ID;
import static com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders.MAPPER_ID;
import static com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders.OBJECT_ID;
import static com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders.SOURCE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.business.model.integration.Integration;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IngressMapperContributions;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IntegrationIngressMappers;
import com.opencop.core.sdk.component.database.AbstractDatabaseIT;
import com.opencop.core.sdk.model.dataimport.ImportEnvelope;
import com.opencop.core.sdk.model.mapper.MappedRecord;
import com.opencop.core.sdk.model.mapper.MappingRequest;
import com.opencop.core.sdk.model.mapper.MappingResponse;
import com.opencop.core.sdk.model.mapper.profile.DynamicMapper;
import com.opencop.core.sdk.ports.gateway.blob.BlobClient;
import com.opencop.core.sdk.ports.gateway.broker.BrokerMessage;
import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.DecodedMappingResult;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.PayloadIngressMapper;
import com.opencop.core.sdk.technical.gateway.mapping.ingress.EventDecoderRegistry;
import com.opencop.core.sdk.technical.gateway.profile.MapperProfileCaches;
import com.opencop.core.sdk.technical.gateway.profile.MapperProfileRepository;
import com.opencop.core.sdk.technical.mapper.MapperFacade;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import com.opencop.core.sdk.testing.integration.NativeIntegrationFixture;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema.TestIntegration;
import com.opencop.processor.business.model.mapping.RealtimeMappingAttempt;
import com.opencop.processor.business.usecase.event.MapRawToGeoHandler;
import com.opencop.processor.component.support.ProcessorComponentTestDatabaseConfig;
import com.opencop.processor.ports.business.observability.mapping.MappingObservationFailureSink;
import com.opencop.processor.ports.business.observability.mapping.RealtimeMappingOutcomeObserver;
import com.opencop.processor.technical.spring.config.CacheConfig;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.geojson.Feature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    classes = PluginMapperIngressIT.TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "redis.host=localhost",
      "redis.port=6379",
      "management.health.redis.enabled=false"
    })
@ActiveProfiles("it")
class PluginMapperIngressIT extends AbstractDatabaseIT {

  private static final Integration TEST_INTEGRATION =
      new TestIntegration(
          UUID.fromString("00000000-0000-4000-8000-0000000000aa"),
          "Test integration",
          "Test integration");

  private static final UUID SELECTED_MAPPER_ID =
      UUID.fromString("00000000-0000-4000-8000-000000000801");
  private static final UUID SELECTED_SOURCE_ID =
      UUID.fromString("00000000-0000-4000-8000-000000000804");

  @Autowired private EventDecoderRegistry registry;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CacheManager cacheManager;
  @Autowired private MapperFacade mapperFacade;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    clearProfileCatalog();
    clearMapperCaches();
    reset(mapperFacade);
    persistSelectedPluginMapper();
    persistSourceSelection();
  }

  @Test
  void realTimeIngressExecutesPersistedSelectionAndReportsLocalVersion() {
    var result =
        registry.decodeAndMapRealTimeResult(
            UUID.randomUUID(),
            new MappingProperties(SELECTED_SOURCE_ID, null, false),
            "real-time payload".getBytes(StandardCharsets.UTF_8));

    assertThat(result.mapperId()).isEqualTo(SELECTED_MAPPER_ID);
    assertThat(result.mapperVersion()).isEqualTo(7);
    assertThat(result.features())
        .singleElement()
        .satisfies(
            feature -> {
              assertThat((Object) feature.getProperty("mappedBy")).isEqualTo("PERSISTED-PLUGIN");
              assertThat((Object) feature.getProperty("payload")).isEqualTo("real-time payload");
            });
  }

  @Test
  void fileImportExecutesPersistedSelectionWithoutVersionMismatchDlt() throws Exception {
    var objectId = UUID.randomUUID();
    var importId = UUID.randomUUID();
    var blobKey = "imports/plugin.bin";
    var importedPayload = "file-import payload".getBytes(StandardCharsets.UTF_8);
    var envelope = new ImportEnvelope(blobKey, "plugin.bin", null, null);
    var envelopeBytes = objectMapper.writeValueAsBytes(envelope);
    var blobClient = mock(BlobClient.class);
    when(blobClient.download(blobKey)).thenReturn(new ByteArrayInputStream(importedPayload));
    var mappedProducer = mock(MessageBrokerProducer.class);
    var rawDltProducer = mock(MessageBrokerProducer.class);
    var handler = handler(mappedProducer, rawDltProducer, blobClient);

    handler.handle(List.of(importRecord(objectId, importId, envelopeBytes)));

    var messages = featureBatchCaptor();
    verify(mappedProducer).sendAll(messages.capture());
    assertThat(messages.getValue())
        .singleElement()
        .extracting(BrokerMessage::event)
        .satisfies(
            feature -> {
              assertThat((Object) feature.getProperty("mappedBy")).isEqualTo("PERSISTED-PLUGIN");
              assertThat((Object) feature.getProperty("payload")).isEqualTo("file-import payload");
            });
    verifyNoInteractions(rawDltProducer);
  }

  @Test
  void realTimeIngressRoutesArchivedMapperUnavailableFailureToRawDlt() {
    jdbc.update("UPDATE profiles.data_mapper SET archived = true WHERE id = ?", SELECTED_MAPPER_ID);
    var mappedProducer = mock(MessageBrokerProducer.class);
    var rawDltProducer = mock(MessageBrokerProducer.class);
    var handler = handler(mappedProducer, rawDltProducer, mock(BlobClient.class));

    handler.handle(
        List.of(
            rawRecord(
                UUID.randomUUID(), "archived real-time payload".getBytes(StandardCharsets.UTF_8))));

    verifyNoInteractions(mappedProducer);
    var dltMessages = byteBatchCaptor();
    verify(rawDltProducer).sendAll(dltMessages.capture());
    assertThat(dltMessages.getValue())
        .singleElement()
        .satisfies(
            message -> {
              assertThat(message.event())
                  .isEqualTo("archived real-time payload".getBytes(StandardCharsets.UTF_8));
              assertThat(message.headers().get("kafka_dlt-exception-message"))
                  .isEqualTo("ingress mapper unavailable");
            });
  }

  @Test
  void fileImportRoutesArchivedMapperUnavailableFailureToRawDlt() throws Exception {
    jdbc.update("UPDATE profiles.data_mapper SET archived = true WHERE id = ?", SELECTED_MAPPER_ID);
    var importId = UUID.randomUUID();
    var blobKey = "imports/archived-plugin.bin";
    var envelope = new ImportEnvelope(blobKey, "archived-plugin.bin", null, null);
    var envelopeBytes = objectMapper.writeValueAsBytes(envelope);
    var blobClient = mock(BlobClient.class);
    when(blobClient.download(blobKey))
        .thenReturn(
            new ByteArrayInputStream("archived plugin payload".getBytes(StandardCharsets.UTF_8)));
    var mappedProducer = mock(MessageBrokerProducer.class);
    var rawDltProducer = mock(MessageBrokerProducer.class);
    var handler = handler(mappedProducer, rawDltProducer, blobClient);

    handler.handle(List.of(importRecord(UUID.randomUUID(), importId, envelopeBytes)));

    verifyNoInteractions(mappedProducer);
    var dltMessages = byteBatchCaptor();
    verify(rawDltProducer).sendAll(dltMessages.capture());
    assertThat(dltMessages.getValue())
        .singleElement()
        .satisfies(
            message -> {
              assertThat(message.event()).isEqualTo(envelopeBytes);
              assertThat(message.headers().get("kafka_dlt-exception-message"))
                  .isEqualTo("ingress mapper unavailable");
            });
  }

  @Test
  void sourceSelectionObservesArchivedStateAfterCacheEviction() {
    var properties = new MappingProperties(SELECTED_SOURCE_ID, null, false);

    assertThat(
            registry.decodeAndMapRealTimeResult(
                UUID.randomUUID(), properties, "before archive".getBytes(StandardCharsets.UTF_8)))
        .extracting(DecodedMappingResult::mapperVersion)
        .isEqualTo(7);

    jdbc.update("UPDATE profiles.data_mapper SET archived = true WHERE id = ?", SELECTED_MAPPER_ID);

    assertThat(
            registry.decodeAndMapRealTimeResult(
                UUID.randomUUID(), properties, "cached active".getBytes(StandardCharsets.UTF_8)))
        .extracting(DecodedMappingResult::mapperVersion)
        .isEqualTo(7);

    cacheManager.getCache(MapperProfileCaches.BY_SOURCE_ID).evict(SELECTED_SOURCE_ID);

    assertThatThrownBy(
            () ->
                registry.decodeAndMapRealTimeResult(
                    UUID.randomUUID(),
                    properties,
                    "after cache eviction".getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("ingress mapper unavailable");
  }

  @Test
  void mapperIdSelectionObservesReenabledStateAfterCacheEviction() throws Exception {
    jdbc.update("UPDATE profiles.data_mapper SET archived = true WHERE id = ?", SELECTED_MAPPER_ID);
    var properties = new MappingProperties(SELECTED_SOURCE_ID, null, false);

    assertThatThrownBy(
            () ->
                registry.decodeAndMapResult(
                    SELECTED_MAPPER_ID,
                    UUID.randomUUID(),
                    properties,
                    "archived".getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("ingress mapper unavailable");

    jdbc.update(
        "UPDATE profiles.data_mapper SET archived = false WHERE id = ?", SELECTED_MAPPER_ID);

    assertThatThrownBy(
            () ->
                registry.decodeAndMapResult(
                    SELECTED_MAPPER_ID,
                    UUID.randomUUID(),
                    properties,
                    "cached archived".getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("ingress mapper unavailable");

    cacheManager.getCache(MapperProfileCaches.BY_MAPPER_ID).evict(SELECTED_MAPPER_ID);

    assertThat(
            registry.decodeAndMapResult(
                SELECTED_MAPPER_ID,
                UUID.randomUUID(),
                properties,
                "reenabled".getBytes(StandardCharsets.UTF_8)))
        .extracting(DecodedMappingResult::mapperVersion)
        .isEqualTo(7);
  }

  @Test
  void activeDataAuthoredMapperRemainsAvailable() throws Exception {
    clearProfileCatalog();
    clearMapperCaches();
    var dynamicMapperId = UUID.fromString("00000000-0000-4000-8000-000000000805");
    var definition = new DynamicMapper(UUID.randomUUID(), true, null, null, List.of());
    insertMapperIdentity(dynamicMapperId);
    jdbc.update(
        """
        INSERT INTO profiles.data_mapper
          (id, version, name, description, editable, mapper, archived)
        VALUES (?, 3, 'Data-authored mapper', 'Persisted mapping rules', true, CAST(? AS jsonb),
                false)
        """,
        dynamicMapperId,
        objectMapper.writeValueAsString(definition));
    var mappedFeature = new Feature();
    mappedFeature.setProperty("mappedBy", "DATA-AUTHORED");
    when(mapperFacade.map(any(MappingRequest.class)))
        .thenReturn(
            new MappingResponse(
                definition.id(),
                null,
                List.of(new MappedRecord(0, List.of(mappedFeature), List.of())),
                List.of(),
                List.of()));

    var result =
        registry.decodeAndMapResult(
            dynamicMapperId,
            UUID.randomUUID(),
            new MappingProperties(SELECTED_SOURCE_ID, null, false),
            "dynamic payload".getBytes(StandardCharsets.UTF_8));

    assertThat(result.mapperId()).isEqualTo(dynamicMapperId);
    assertThat(result.mapperVersion()).isEqualTo(3);
    assertThat(result.features()).containsExactly(mappedFeature);
    verify(mapperFacade).map(any(MappingRequest.class));
  }

  @Test
  void userOwnedDataAuthoredCollisionExecutesPersistedRulesInsteadOfContribution()
      throws Exception {
    var definition = new DynamicMapper(UUID.randomUUID(), true, null, null, List.of());
    jdbc.update(
        """
        UPDATE profiles.data_mapper
        SET editable = true, mapper = CAST(? AS jsonb)
        WHERE id = ?
        """,
        objectMapper.writeValueAsString(definition),
        SELECTED_MAPPER_ID);
    jdbc.update(
        "UPDATE profiles.mapper SET integration_id = NULL WHERE id = ?", SELECTED_MAPPER_ID);
    clearMapperCaches();
    var mappedFeature = new Feature();
    mappedFeature.setProperty("mappedBy", "DATA-AUTHORED");
    when(mapperFacade.map(any(MappingRequest.class)))
        .thenReturn(
            new MappingResponse(
                definition.id(),
                null,
                List.of(new MappedRecord(0, List.of(mappedFeature), List.of())),
                List.of(),
                List.of()));

    var result =
        registry.decodeAndMapRealTimeResult(
            UUID.randomUUID(),
            new MappingProperties(SELECTED_SOURCE_ID, null, false),
            "dynamic payload".getBytes(StandardCharsets.UTF_8));

    assertThat(result.mapperId()).isEqualTo(SELECTED_MAPPER_ID);
    assertThat(result.mapperVersion()).isEqualTo(41);
    assertThat(result.features()).containsExactly(mappedFeature);
    verify(mapperFacade).map(any(MappingRequest.class));
  }

  private MapRawToGeoHandler handler(
      MessageBrokerProducer<Feature> mappedProducer,
      MessageBrokerProducer<byte[]> rawDltProducer,
      BlobClient blobClient) {
    return new MapRawToGeoHandler(
        registry,
        mappedProducer,
        objectMapper,
        blobClient,
        rawDltProducer,
        NO_OP_MAPPING_OBSERVER,
        NO_OP_OBSERVATION_FAILURE_SINK);
  }

  private static final RealtimeMappingOutcomeObserver NO_OP_MAPPING_OBSERVER =
      new RealtimeMappingOutcomeObserver() {
        @Override
        public void observe(RealtimeMappingAttempt attempt, DecodedMappingResult result) {}

        @Override
        public void observeThrown(RealtimeMappingAttempt attempt) {}
      };

  private static final MappingObservationFailureSink NO_OP_OBSERVATION_FAILURE_SINK = _ -> {};

  private static ConsumerRecord<String, byte[]> importRecord(
      UUID objectId, UUID importId, byte[] value) {
    var record = rawRecord(objectId, value);
    record.headers().add(IMPORT_ID.getName(), importId.toString().getBytes(StandardCharsets.UTF_8));
    record
        .headers()
        .add(MAPPER_ID.getName(), SELECTED_MAPPER_ID.toString().getBytes(StandardCharsets.UTF_8))
        .add(CONTENT_KIND.getName(), ImportEnvelope.CONTENT_KIND.getBytes(StandardCharsets.UTF_8));
    return record;
  }

  private static ConsumerRecord<String, byte[]> rawRecord(UUID objectId, byte[] value) {
    var record =
        new ConsumerRecord<String, byte[]>(
            "opencop.event.external.raw", 0, 0L, "ingress-key", value);
    record
        .headers()
        .add(SOURCE_ID.getName(), SELECTED_SOURCE_ID.toString().getBytes(StandardCharsets.UTF_8))
        .add(OBJECT_ID.getName(), objectId.toString().getBytes(StandardCharsets.UTF_8));
    return record;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static ArgumentCaptor<Collection<BrokerMessage<Feature>>> featureBatchCaptor() {
    return ArgumentCaptor.forClass(Collection.class);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static ArgumentCaptor<Collection<BrokerMessage<byte[]>>> byteBatchCaptor() {
    return ArgumentCaptor.forClass(Collection.class);
  }

  /** The selected mapper as Core's registration records it: contributed by the test plugin. */
  private void persistSelectedPluginMapper() {
    jdbc.update(
        """
        INSERT INTO config.integration (id, name, description)
        VALUES (?, ?, ?)
        ON CONFLICT (id) DO NOTHING
        """,
        TEST_INTEGRATION.id(),
        TEST_INTEGRATION.name(),
        TEST_INTEGRATION.description());
    insertMapperIdentity(SELECTED_MAPPER_ID, TEST_INTEGRATION.id());
    jdbc.update(
        """
        INSERT INTO profiles.data_mapper
          (id, version, name, description, editable, mapper, archived)
        VALUES (?, 41, 'Persisted plugin metadata', 'Persisted description', false,
                CAST('null' AS jsonb), false)
        """,
        SELECTED_MAPPER_ID);
  }

  private void insertMapperIdentity(UUID mapperId) {
    insertMapperIdentity(mapperId, null);
  }

  private void insertMapperIdentity(UUID mapperId, UUID integrationId) {
    jdbc.update(
        """
        INSERT INTO profiles.mapper (id, qualifier, integration_id)
        VALUES (?, 'INGRESS'::profiles.mapper_qualifier, ?)
        ON CONFLICT (id) DO NOTHING
        """,
        mapperId,
        integrationId);
  }

  private void persistSourceSelection() {
    NativeIntegrationFixture.seed(jdbc);
    jdbc.update(
        """
        INSERT INTO profiles.data_source
          (id, name, ingress_mapper_id, connection_spec, integration_id, connection_schema_version)
        VALUES (?, 'Plugin source', ?, CAST('{}' AS jsonb), ?, ?)
        """,
        SELECTED_SOURCE_ID,
        SELECTED_MAPPER_ID,
        NativeIntegrationFixture.CONNECTION_SCHEMA.integrationId(),
        NativeIntegrationFixture.CONNECTION_SCHEMA.schemaVersion());
  }

  private void clearProfileCatalog() {
    jdbc.update("DELETE FROM profiles.data_source");
    jdbc.update("DELETE FROM profiles.data_mapper");
    jdbc.update("DELETE FROM profiles.mapper");
  }

  private void clearMapperCaches() {
    cacheManager.getCache(MapperProfileCaches.BY_SOURCE_ID).clear();
    cacheManager.getCache(MapperProfileCaches.BY_MAPPER_ID).clear();
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EnableCaching
  @Import({
    ProcessorComponentTestDatabaseConfig.class,
    MapperProfileRepository.class,
    EventDecoderRegistry.class
  })
  static class TestApplication {

    @Bean
    TestPluginMapper testPluginMapper() {
      return new TestPluginMapper();
    }

    @Bean
    IngressMapperContributions ingressMapperContributions(TestPluginMapper mapper) {
      return IngressMapperContributions.of(
          List.of(new IntegrationIngressMappers(TEST_INTEGRATION, List.of(mapper))));
    }

    @Bean
    MapperFacade mapperFacade() {
      return mock(MapperFacade.class);
    }

    @Bean
    CacheManager cacheManager() {
      return new CacheConfig().cacheManager();
    }
  }

  static final class TestPluginMapper implements PayloadIngressMapper<Feature> {

    @Override
    public UUID id() {
      return SELECTED_MAPPER_ID;
    }

    @Override
    public String name() {
      return "PERSISTED-PLUGIN";
    }

    @Override
    public String description() {
      return "Processor persisted ingress fixture";
    }

    @Override
    public int version() {
      return 7;
    }

    @Override
    public List<Feature> map(UUID id, MappingProperties properties, byte[] input) {
      var feature = new Feature();
      feature.setProperty("mappedBy", name());
      feature.setProperty("payload", new String(input, StandardCharsets.UTF_8));
      return List.of(feature);
    }
  }
}
