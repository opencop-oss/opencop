// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.event;

import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_BEHAVIOR;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_EXTENSION;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_INPUT;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_MAPPING;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_SCHEMA;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_SETTINGS;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.EGRESS_MAPPING;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.GEOCODING;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.INGRESS_MAPPING;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.INTEGRATION_IDENTITY;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IntegrationIngressMappers;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.model.dataimport.ImportEnvelope;
import com.opencop.core.sdk.model.mapper.MappingError;
import com.opencop.core.sdk.model.mapper.MappingResponse;
import com.opencop.core.sdk.model.mapper.config.MapperFormat;
import com.opencop.core.sdk.ports.gateway.blob.BlobClient;
import com.opencop.core.sdk.ports.gateway.broker.BrokerMessage;
import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.DecodedMappingResult;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.MappingDiagnostic;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.MappingDiagnosticSeverity;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.PayloadIngressMapper;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.WriteOnlySourceException;
import com.opencop.core.sdk.technical.config.FrameworkCompatibleConfigurationBeanNameGenerator;
import com.opencop.core.sdk.technical.config.integration.EnableIntegrationCapabilities;
import com.opencop.core.sdk.technical.gateway.extractor.KmzIconRef;
import com.opencop.core.sdk.technical.gateway.mapping.ingress.EventDecoderRegistry;
import com.opencop.core.sdk.technical.mapper.MapperFacade;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.elements.Element;
import com.opencop.core.sdk.technical.model.opencop.elements.ElementTypes;
import com.opencop.core.sdk.technical.model.opencop.elements.MediaIconElement;
import com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema.TestIntegration;
import com.opencop.processor.business.model.mapping.MappingResourceKind;
import com.opencop.processor.business.model.mapping.RealtimeMappingAttempt;
import com.opencop.processor.business.model.mapping.RealtimeMappingEntry;
import com.opencop.processor.technical.observability.mapping.MappingObservationFailureReporter;
import com.opencop.processor.technical.observability.mapping.RealtimeMappingOutcomeObservability;
import com.opencop.processor.technical.spring.dlt.DeadLetterFailureIndexer;
import com.opencop.processor.technical.spring.dlt.DeadLetterHeaders;
import com.opencop.processor.testfixture.mapping.SelectedPluginMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.geojson.Feature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import tools.jackson.databind.ObjectMapper;

class MapRawToGeoHandlerTest {

  private static final UUID PLUGIN_INTEGRATION_ID =
      UUID.fromString("00000000-0000-4000-8000-0000000000aa");

  private static final ApplicationContextRunner INGRESS_REGISTRY_CONTEXT =
      applicationContext().withUserConfiguration(IngressRegistryConfig.class);

  private static ApplicationContextRunner applicationContext() {
    return new ApplicationContextRunner(
        () -> {
          var context = new AnnotationConfigApplicationContext();
          context.setBeanNameGenerator(FrameworkCompatibleConfigurationBeanNameGenerator.INSTANCE);
          return context;
        });
  }

  @Nested
  class MediaHrefTests {

    private MapRawToGeoHandler handler;

    @BeforeEach
    void setUp() {
      handler = new MapRawToGeoHandler(null, null, null, null, null, null, null);
    }

    @Test
    void resolveMediaHrefs_replacesMediaUrnWithCoreUrl() {
      UUID mediaId = UUID.randomUUID();
      UUID layerId = UUID.randomUUID();
      Feature feature = featureWithMediaHref(mediaId);
      List<KmzIconRef> manifest =
          List.of(
              new KmzIconRef(
                  "icons/icon.png",
                  "icon.png",
                  "icons/icon.png",
                  mediaId,
                  KmzIconRef.Status.RESOLVED,
                  64,
                  48));

      handler.resolveMediaHrefs(List.of(feature), layerId, manifest);

      OpenCopProperties oc = (OpenCopProperties) feature.getProperties().get("opencop");
      String href = (String) oc.getElement().get(MediaIconElement.HREF);
      assertEquals("opencop:url:/v1/layers/" + layerId + "/media/" + mediaId + "#64x48", href);
      assertNull(oc.getElement().get("symbol"));
    }

    @Test
    void resolveMediaHrefs_leavesSymbolCodesAlone() {
      UUID layerId = UUID.randomUUID();
      UUID mediaId = UUID.randomUUID();
      String sidc = "SFGPUCI---*****";
      Feature feature = featureWithSymbol(sidc);
      List<KmzIconRef> manifest =
          List.of(
              new KmzIconRef(
                  "icons/icon.png",
                  "icon.png",
                  "icons/icon.png",
                  mediaId,
                  KmzIconRef.Status.RESOLVED,
                  64,
                  48));

      handler.resolveMediaHrefs(List.of(feature), layerId, manifest);

      OpenCopProperties oc = (OpenCopProperties) feature.getProperties().get("opencop");
      String symbol = (String) oc.getElement().get("symbol");
      assertEquals(sidc, symbol);
    }

    @Test
    void resolveMediaHrefs_skipsFeatureWithoutOpenCopProperties() {
      UUID layerId = UUID.randomUUID();
      Feature feature = new Feature();
      feature.setProperty("someOther", "value");

      assertDoesNotThrow(() -> handler.resolveMediaHrefs(List.of(feature), layerId, List.of()));
    }

    @Test
    void buildDimensionFragment_fallsBackToDefault() {
      UUID mediaId = UUID.randomUUID();
      UUID otherMediaId = UUID.randomUUID();
      List<KmzIconRef> manifest =
          List.of(
              new KmzIconRef(
                  "icons/icon.png",
                  "icon.png",
                  "icons/icon.png",
                  otherMediaId,
                  KmzIconRef.Status.RESOLVED,
                  128,
                  128));

      String fragment = handler.buildDimensionFragment(mediaId.toString(), manifest);

      assertEquals("#64x64", fragment);
    }
  }

  @Nested
  @SuppressWarnings("unchecked")
  class RoutingTests {

    private EventDecoderRegistry registry;
    private MessageBrokerProducer<Feature> producer;
    private MessageBrokerProducer<byte[]> rawDltProducer;
    private ObjectMapper objectMapper;
    private BlobClient blobClient;
    private RealtimeMappingOutcomeObservability mappingObservability;
    private MappingObservationFailureReporter observationFailureReporter;
    private MapRawToGeoHandler handler;

    @BeforeEach
    void setUp() {
      registry = mock(EventDecoderRegistry.class);
      producer = mock(MessageBrokerProducer.class);
      rawDltProducer = mock(MessageBrokerProducer.class);
      objectMapper = mock(ObjectMapper.class);
      blobClient = mock(BlobClient.class);
      mappingObservability = mock(RealtimeMappingOutcomeObservability.class);
      observationFailureReporter = mock(MappingObservationFailureReporter.class);
      handler =
          new MapRawToGeoHandler(
              registry,
              producer,
              objectMapper,
              blobClient,
              rawDltProducer,
              mappingObservability,
              observationFailureReporter);
    }

    @Test
    void handle_importEnvelope_downloadsBlobAndDecodesWithMapperId() throws Exception {
      var sourceId = UUID.randomUUID();
      var objectId = UUID.randomUUID();
      var mapperId = UUID.randomUUID();
      var importId = UUID.randomUUID();
      var blobKey = "imports/2026/03/20/test-file.csv";
      var envelopeJson = "{\"blobKey\":\"" + blobKey + "\",\"filename\":\"test.csv\"}";
      var downloadedBytes = new byte[] {1, 2, 3, 4, 5};
      var feature = new Feature();

      var consumerRecord =
          new ConsumerRecord<String, byte[]>(
              "opencop.event.external.raw",
              0,
              0L,
              null,
              envelopeJson.getBytes(StandardCharsets.UTF_8));
      consumerRecord
          .headers()
          .add(
              OpencopKafkaHeaders.SOURCE_ID.getName(),
              sourceId.toString().getBytes(StandardCharsets.UTF_8))
          .add(
              OpencopKafkaHeaders.OBJECT_ID.getName(),
              objectId.toString().getBytes(StandardCharsets.UTF_8))
          .add(
              OpencopKafkaHeaders.IMPORT_ID.getName(),
              importId.toString().getBytes(StandardCharsets.UTF_8))
          .add(
              OpencopKafkaHeaders.MAPPER_ID.getName(),
              mapperId.toString().getBytes(StandardCharsets.UTF_8))
          .add(
              OpencopKafkaHeaders.CONTENT_KIND.getName(),
              ImportEnvelope.CONTENT_KIND.getBytes(StandardCharsets.UTF_8));

      var envelope = new ImportEnvelope(blobKey, "test.csv", null, null);
      when(objectMapper.readValue(consumerRecord.value(), ImportEnvelope.class))
          .thenReturn(envelope);
      when(blobClient.download(blobKey)).thenReturn(new ByteArrayInputStream(downloadedBytes));
      when(registry.decodeAndMapResult(
              eq(mapperId), eq(objectId), any(MappingProperties.class), eq(downloadedBytes)))
          .thenReturn(DecodedMappingResult.fromFeatures(mapperId, 1, "KML-KMZ", List.of(feature)));

      handler.handle(List.of(consumerRecord));

      verify(blobClient).download(blobKey);
      verify(registry)
          .decodeAndMapResult(
              eq(mapperId), eq(objectId), any(MappingProperties.class), eq(downloadedBytes));
      verifyNoInteractions(mappingObservability);
      var mappedCaptor = featureBatchCaptor();
      verify(producer).sendAll(mappedCaptor.capture());
      assertEquals(List.of(feature), events(mappedCaptor.getValue()));
    }

    @Test
    void handle_importEnvelope_executesTheSelectedPluginMapperOnDownloadedBytes() throws Exception {
      var sourceId = UUID.randomUUID();
      var objectId = UUID.randomUUID();
      var importId = UUID.randomUUID();
      var blobKey = "imports/2026/09/11/plugin.bin";
      var envelopeBytes = "plugin envelope".getBytes(StandardCharsets.UTF_8);
      var downloadedBytes = "plugin payload".getBytes(StandardCharsets.UTF_8);

      INGRESS_REGISTRY_CONTEXT.run(
          context -> {
            var pluginMapper = context.getBean(SelectedPluginMapper.class);
            var persistedMapper =
                mapperProfile(pluginMapper).toBuilder()
                    .withIntegrationId(PLUGIN_INTEGRATION_ID)
                    .withVersion(41)
                    .build();
            when(context.getBean(MapperProfileService.class).findLatest(pluginMapper.id()))
                .thenReturn(Optional.of(persistedMapper));
            var envelope = new ImportEnvelope(blobKey, "plugin.bin", null, null);
            var consumerRecord =
                importRecord(sourceId, objectId, pluginMapper.id(), importId, envelopeBytes);
            when(objectMapper.readValue(envelopeBytes, ImportEnvelope.class)).thenReturn(envelope);
            when(blobClient.download(blobKey))
                .thenReturn(new ByteArrayInputStream(downloadedBytes));
            var actualProducer = mock(MessageBrokerProducer.class);
            var actualHandler =
                new MapRawToGeoHandler(
                    context.getBean(EventDecoderRegistry.class),
                    actualProducer,
                    objectMapper,
                    blobClient,
                    rawDltProducer,
                    mappingObservability,
                    observationFailureReporter);

            actualHandler.handle(List.of(consumerRecord));

            var messages = featureBatchCaptor();
            verify(actualProducer).sendAll(messages.capture());
            assertEquals(
                "SELECTED-PLUGIN", events(messages.getValue()).getFirst().getProperty("mappedBy"));
            assertEquals(
                "plugin payload", events(messages.getValue()).getFirst().getProperty("payload"));
            verifyNoInteractions(rawDltProducer);
          });
    }

    @Test
    void handle_importEnvelope_routesArchivedMapperUnavailableFailureToRawDlt() throws Exception {
      var sourceId = UUID.randomUUID();
      var objectId = UUID.randomUUID();
      var importId = UUID.randomUUID();
      var blobKey = "imports/2026/09/14/archived-plugin.bin";
      var envelopeBytes = "plugin envelope".getBytes(StandardCharsets.UTF_8);
      var downloadedBytes = "plugin payload".getBytes(StandardCharsets.UTF_8);

      INGRESS_REGISTRY_CONTEXT.run(
          context -> {
            var pluginMapper = context.getBean(SelectedPluginMapper.class);
            var archivedMapper =
                mapperProfile(pluginMapper).toBuilder()
                    .withIntegrationId(PLUGIN_INTEGRATION_ID)
                    .withArchived(true)
                    .build();
            when(context.getBean(MapperProfileService.class).findLatest(pluginMapper.id()))
                .thenReturn(Optional.of(archivedMapper));
            var envelope = new ImportEnvelope(blobKey, "archived-plugin.bin", null, null);
            var consumerRecord =
                importRecord(sourceId, objectId, pluginMapper.id(), importId, envelopeBytes);
            when(objectMapper.readValue(envelopeBytes, ImportEnvelope.class)).thenReturn(envelope);
            when(blobClient.download(blobKey))
                .thenReturn(new ByteArrayInputStream(downloadedBytes));
            var actualProducer = mock(MessageBrokerProducer.class);
            var actualHandler =
                new MapRawToGeoHandler(
                    context.getBean(EventDecoderRegistry.class),
                    actualProducer,
                    objectMapper,
                    blobClient,
                    rawDltProducer,
                    mappingObservability,
                    observationFailureReporter);

            actualHandler.handle(List.of(consumerRecord));

            verifyNoInteractions(actualProducer);
            var dltMessages = byteBatchCaptor();
            verify(rawDltProducer).sendAll(dltMessages.capture());
            assertEquals(
                "ingress mapper unavailable",
                onlyMessage(dltMessages.getValue()).headers().get("kafka_dlt-exception-message"));
          });
    }

    @Test
    void handle_importEnvelope_logs_mapper_errors_with_import_context() throws Exception {
      var sourceId = UUID.randomUUID();
      var objectId = UUID.randomUUID();
      var mapperId = UUID.randomUUID();
      var importId = UUID.randomUUID();
      var blobKey = "imports/2026/03/20/test-file.kmz";
      var envelopeJson = "{\"blobKey\":\"" + blobKey + "\",\"filename\":\"TC Map Outlines.kmz\"}";
      var downloadedBytes = new byte[] {1, 2, 3, 4, 5};

      var consumerRecord =
          new ConsumerRecord<String, byte[]>(
              "opencop.event.external.raw",
              0,
              0L,
              null,
              envelopeJson.getBytes(StandardCharsets.UTF_8));
      consumerRecord
          .headers()
          .add(
              OpencopKafkaHeaders.SOURCE_ID.getName(),
              sourceId.toString().getBytes(StandardCharsets.UTF_8))
          .add(
              OpencopKafkaHeaders.OBJECT_ID.getName(),
              objectId.toString().getBytes(StandardCharsets.UTF_8))
          .add(
              OpencopKafkaHeaders.IMPORT_ID.getName(),
              importId.toString().getBytes(StandardCharsets.UTF_8))
          .add(
              OpencopKafkaHeaders.MAPPER_ID.getName(),
              mapperId.toString().getBytes(StandardCharsets.UTF_8))
          .add(
              OpencopKafkaHeaders.CONTENT_KIND.getName(),
              ImportEnvelope.CONTENT_KIND.getBytes(StandardCharsets.UTF_8));

      var envelope = new ImportEnvelope(blobKey, "TC Map Outlines.kmz", null, null);
      when(objectMapper.readValue(consumerRecord.value(), ImportEnvelope.class))
          .thenReturn(envelope);
      when(blobClient.download(blobKey)).thenReturn(new ByteArrayInputStream(downloadedBytes));
      when(registry.decodeAndMapResult(
              eq(mapperId), eq(objectId), any(MappingProperties.class), eq(downloadedBytes)))
          .thenReturn(
              DecodedMappingResult.fromMappingResponse(
                  mapperId,
                  1,
                  "KML-KMZ",
                  new MappingResponse(
                      mapperId,
                      MapperFormat.KML_KMZ,
                      List.of(),
                      List.of(
                          new MappingError(
                              null,
                              "FEATURE_BUILDER_ERROR",
                              "The mapping for STYLE_FILL_COLOR is not a String",
                              null,
                              0),
                          new MappingError(
                              null,
                              "FEATURE_BUILDER_ERROR",
                              "The mapping for STYLE_FILL_COLOR is not a String",
                              null,
                              1)),
                      List.of())));

      var appender = attachAppender();

      try {
        handler.handle(List.of(consumerRecord));

        var messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertEquals(2, messages.size());
        assertTrue(
            messages
                .getFirst()
                .contains(
                    "Import "
                        + importId
                        + " for source "
                        + sourceId
                        + " using mapper 'KML-KMZ' on file 'TC Map Outlines.kmz' produced 0 feature(s), 0 mapped record(s), 2 error(s), 0 warning(s)"));
        assertTrue(
            messages
                .get(1)
                .contains(
                    "Import "
                        + importId
                        + " first mapper error: FEATURE_BUILDER_ERROR :: The mapping for STYLE_FILL_COLOR is not a String"));
        verifyNoInteractions(producer);
      } finally {
        detachAppender(appender);
      }
    }

    @Test
    void handle_importEnvelope_logs_info_summary_when_zero_output_has_no_mapper_errors()
        throws Exception {
      var sourceId = UUID.randomUUID();
      var objectId = UUID.randomUUID();
      var mapperId = UUID.randomUUID();
      var importId = UUID.randomUUID();
      var blobKey = "imports/2026/03/20/empty-file.kmz";
      var envelopeJson = "{\"blobKey\":\"" + blobKey + "\",\"filename\":\"empty.kmz\"}";
      var downloadedBytes = new byte[] {1, 2, 3, 4, 5};

      var consumerRecord =
          new ConsumerRecord<String, byte[]>(
              "opencop.event.external.raw",
              0,
              0L,
              null,
              envelopeJson.getBytes(StandardCharsets.UTF_8));
      consumerRecord
          .headers()
          .add(
              OpencopKafkaHeaders.SOURCE_ID.getName(),
              sourceId.toString().getBytes(StandardCharsets.UTF_8))
          .add(
              OpencopKafkaHeaders.OBJECT_ID.getName(),
              objectId.toString().getBytes(StandardCharsets.UTF_8))
          .add(
              OpencopKafkaHeaders.IMPORT_ID.getName(),
              importId.toString().getBytes(StandardCharsets.UTF_8))
          .add(
              OpencopKafkaHeaders.MAPPER_ID.getName(),
              mapperId.toString().getBytes(StandardCharsets.UTF_8))
          .add(
              OpencopKafkaHeaders.CONTENT_KIND.getName(),
              ImportEnvelope.CONTENT_KIND.getBytes(StandardCharsets.UTF_8));

      var envelope = new ImportEnvelope(blobKey, "empty.kmz", null, null);
      when(objectMapper.readValue(consumerRecord.value(), ImportEnvelope.class))
          .thenReturn(envelope);
      when(blobClient.download(blobKey)).thenReturn(new ByteArrayInputStream(downloadedBytes));
      when(registry.decodeAndMapResult(
              eq(mapperId), eq(objectId), any(MappingProperties.class), eq(downloadedBytes)))
          .thenReturn(
              DecodedMappingResult.fromMappingResponse(
                  mapperId,
                  1,
                  "KML-KMZ",
                  new MappingResponse(
                      mapperId, MapperFormat.KML_KMZ, List.of(), List.of(), List.of())));

      var appender = attachAppender();

      try {
        handler.handle(List.of(consumerRecord));

        assertEquals(1, appender.list.size());
        assertEquals(Level.INFO, appender.list.getFirst().getLevel());
        var message = appender.list.getFirst().getFormattedMessage();
        assertTrue(message.contains("using mapper 'KML-KMZ' on file 'empty.kmz'"));
        assertTrue(message.contains("features=0, mapped records=0, warnings=0"));
        verifyNoInteractions(producer);
      } finally {
        detachAppender(appender);
      }
    }

    @Test
    void handle_importEnvelope_logs_import_context_when_mapping_throws() throws Exception {
      var sourceId = UUID.randomUUID();
      var objectId = UUID.randomUUID();
      var mapperId = UUID.randomUUID();
      var importId = UUID.randomUUID();
      var blobKey = "imports/2026/03/20/test-file.json";
      var envelopeJson = "{\"blobKey\":\"" + blobKey + "\",\"filename\":\"test.json\"}";
      var downloadedBytes = new byte[] {1, 2, 3, 4, 5};

      var consumerRecord =
          new ConsumerRecord<String, byte[]>(
              "opencop.event.external.raw",
              0,
              0L,
              "key",
              envelopeJson.getBytes(StandardCharsets.UTF_8));
      consumerRecord
          .headers()
          .add(
              OpencopKafkaHeaders.SOURCE_ID.getName(),
              sourceId.toString().getBytes(StandardCharsets.UTF_8))
          .add(
              OpencopKafkaHeaders.OBJECT_ID.getName(),
              objectId.toString().getBytes(StandardCharsets.UTF_8))
          .add(
              OpencopKafkaHeaders.IMPORT_ID.getName(),
              importId.toString().getBytes(StandardCharsets.UTF_8))
          .add(
              OpencopKafkaHeaders.MAPPER_ID.getName(),
              mapperId.toString().getBytes(StandardCharsets.UTF_8))
          .add(
              OpencopKafkaHeaders.CONTENT_KIND.getName(),
              ImportEnvelope.CONTENT_KIND.getBytes(StandardCharsets.UTF_8));

      var envelope = new ImportEnvelope(blobKey, "test.json", null, null);
      when(objectMapper.readValue(consumerRecord.value(), ImportEnvelope.class))
          .thenReturn(envelope);
      when(blobClient.download(blobKey)).thenReturn(new ByteArrayInputStream(downloadedBytes));
      when(registry.decodeAndMapResult(
              eq(mapperId), eq(objectId), any(MappingProperties.class), eq(downloadedBytes)))
          .thenThrow(new IllegalArgumentException("Bad payload token"));

      var appender = attachAppender();

      try {
        handler.handle(List.of(consumerRecord));

        var messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertTrue(
            messages.stream()
                .anyMatch(
                    message ->
                        message.contains(
                            "Import "
                                + importId
                                + " for source "
                                + sourceId
                                + " using mapper "
                                + mapperId
                                + " on file 'test.json' failed before mapping completed: IllegalArgumentException: Bad payload token")));
        var dltCaptor = byteBatchCaptor();
        verify(rawDltProducer).sendAll(dltCaptor.capture());
        BrokerMessage<byte[]> dltRecord = onlyMessage(dltCaptor.getValue());
        assertArrayEquals(consumerRecord.value(), dltRecord.event());
        assertEquals(
            """
            {"sourceId":"%s","mapperId":"%s","importId":"%s"}
            """
                .formatted(sourceId, mapperId, importId)
                .trim(),
            dltRecord.headers().get(DeadLetterFailureIndexer.CONTEXT_HEADER));
      } finally {
        detachAppender(appender);
      }
    }

    @Test
    void handle_rawBytesNoMapper_decodesWithSourceLookup() {
      var sourceId = UUID.randomUUID();
      var objectId = UUID.randomUUID();
      var rawBytes = new byte[] {10, 20, 30};
      var feature = new Feature();

      // No CONTENT_KIND header, no MAPPER_ID header — this is the Connect raw-message flow
      var consumerRecord =
          new ConsumerRecord<String, byte[]>("opencop.event.external.raw", 0, 0L, null, rawBytes);
      consumerRecord
          .headers()
          .add(
              OpencopKafkaHeaders.SOURCE_ID.getName(),
              sourceId.toString().getBytes(StandardCharsets.UTF_8))
          .add(
              OpencopKafkaHeaders.OBJECT_ID.getName(),
              objectId.toString().getBytes(StandardCharsets.UTF_8));

      when(registry.decodeAndMapRealTimeResult(
              eq(objectId), any(MappingProperties.class), eq(rawBytes)))
          .thenReturn(result(List.of(feature)));

      handler.handle(List.of(consumerRecord));

      verifyNoInteractions(blobClient);
      verify(registry)
          .decodeAndMapRealTimeResult(eq(objectId), any(MappingProperties.class), eq(rawBytes));
      var mappedCaptor = featureBatchCaptor();
      verify(producer).sendAll(mappedCaptor.capture());
      assertEquals(List.of(feature), events(mappedCaptor.getValue()));
      verify(mappingObservability).observe(any(RealtimeMappingAttempt.class), any());
    }

    @Test
    void handle_structuredFailedAllObservesWithoutFeatureOrDltPublication() {
      var sourceId = UUID.randomUUID();
      var objectId = UUID.randomUUID();
      var rawBytes = new byte[] {10};
      var record =
          rawRecord("opencop.event.external.raw", 2, 19L, null, rawBytes, sourceId, objectId);
      var result = failedResult();
      when(registry.decodeAndMapRealTimeResult(
              eq(objectId), any(MappingProperties.class), eq(rawBytes)))
          .thenReturn(result);

      handler.handle(List.of(record));

      verify(mappingObservability).observe(any(RealtimeMappingAttempt.class), eq(result));
      verify(mappingObservability, never()).observeThrown(any());
      verifyNoInteractions(producer, rawDltProducer);
    }

    @Test
    void handle_mapperHintUsesRichRealTimeApiNotImportApi() throws Exception {
      var sourceId = UUID.randomUUID();
      var objectId = UUID.randomUUID();
      var mapperId = UUID.randomUUID();
      var rawBytes = new byte[] {11};
      var record =
          rawRecord("opencop.event.external.raw", 0, 0L, null, rawBytes, sourceId, objectId);
      record
          .headers()
          .add(
              OpencopKafkaHeaders.MAPPER_ID.getName(),
              mapperId.toString().getBytes(StandardCharsets.UTF_8));
      when(registry.decodeAndMapRealTimeResult(
              eq(mapperId), eq(objectId), any(MappingProperties.class), eq(rawBytes)))
          .thenReturn(result(List.of()));

      handler.handle(List.of(record));

      verify(registry)
          .decodeAndMapRealTimeResult(
              eq(mapperId), eq(objectId), any(MappingProperties.class), eq(rawBytes));
      verify(registry, never()).decodeAndMapResult(any(), any(), any(), any());
    }

    @Test
    void handle_mapperHintedThrownDecodeRecordsThrownOutcomeAndRoutesToDlt() {
      var sourceId = UUID.randomUUID();
      var objectId = UUID.randomUUID();
      var mapperId = UUID.randomUUID();
      var rawBytes = new byte[] {12};
      var hinted =
          rawRecord("opencop.event.external.raw", 1, 7L, "hinted", rawBytes, sourceId, objectId);
      hinted
          .headers()
          .add(
              OpencopKafkaHeaders.MAPPER_ID.getName(),
              mapperId.toString().getBytes(StandardCharsets.UTF_8));
      when(registry.decodeAndMapRealTimeResult(
              eq(mapperId), eq(objectId), any(MappingProperties.class), eq(rawBytes)))
          .thenThrow(new IllegalArgumentException("hinted decode failed"));

      handler.handle(List.of(hinted));

      var attemptCaptor = ArgumentCaptor.forClass(RealtimeMappingAttempt.class);
      verify(mappingObservability).observeThrown(attemptCaptor.capture());
      assertEquals(sourceId, attemptCaptor.getValue().resource().id());
      assertEquals(1, attemptCaptor.getValue().partition());
      assertEquals(7L, attemptCaptor.getValue().offset());
      verify(mappingObservability, never()).observe(any(), any());
      verify(rawDltProducer).sendAll(any(Collection.class));
      verifyNoInteractions(producer);
    }

    @Test
    void handle_emptyImportMarkerSkipsTelemetryAndKeepsLegacyDecodePath() {
      var sourceId = UUID.randomUUID();
      var objectId = UUID.randomUUID();
      var rawBytes = new byte[] {12};
      var record =
          rawRecord("opencop.event.external.raw", 0, 0L, null, rawBytes, sourceId, objectId);
      record.headers().add(OpencopKafkaHeaders.IMPORT_ID.getName(), new byte[0]);
      when(registry.decodeAndMap(eq(objectId), any(MappingProperties.class), eq(rawBytes)))
          .thenReturn(List.of());

      handler.handle(List.of(record));

      verify(registry).decodeAndMap(eq(objectId), any(MappingProperties.class), eq(rawBytes));
      verifyNoInteractions(mappingObservability);
    }

    @Test
    void handle_validLayerMarkerSkipsTelemetryAndKeepsLegacyDecodePath() {
      var sourceId = UUID.randomUUID();
      var objectId = UUID.randomUUID();
      var rawBytes = new byte[] {14};
      var record =
          rawRecord("opencop.event.external.raw", 0, 0L, null, rawBytes, sourceId, objectId);
      record
          .headers()
          .add(
              OpencopKafkaHeaders.LAYER_ID.getName(),
              UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
      when(registry.decodeAndMap(eq(objectId), any(MappingProperties.class), eq(rawBytes)))
          .thenReturn(List.of());

      handler.handle(List.of(record));

      verify(registry).decodeAndMap(eq(objectId), any(MappingProperties.class), eq(rawBytes));
      verifyNoInteractions(mappingObservability);
    }

    @Test
    void handle_malformedImportMarkerCreatesExistingRecordLocalDltWithoutTelemetry() {
      var sourceId = UUID.randomUUID();
      var objectId = UUID.randomUUID();
      var rawBytes = new byte[] {15};
      var record =
          rawRecord("opencop.event.external.raw", 0, 0L, null, rawBytes, sourceId, objectId);
      record
          .headers()
          .add(
              OpencopKafkaHeaders.IMPORT_ID.getName(),
              "not-a-uuid".getBytes(StandardCharsets.UTF_8));

      handler.handle(List.of(record));

      verifyNoInteractions(registry, mappingObservability, producer);
      verify(rawDltProducer).sendAll(any(Collection.class));
    }

    @Test
    void handle_observerAndReporterFailureDoNotCreateDltOrPreventPublication() {
      var sourceId = UUID.randomUUID();
      var objectId = UUID.randomUUID();
      var rawBytes = new byte[] {13};
      var feature = new Feature();
      var record =
          rawRecord("opencop.event.external.raw", 0, 0L, null, rawBytes, sourceId, objectId);
      when(registry.decodeAndMapRealTimeResult(
              eq(objectId), any(MappingProperties.class), eq(rawBytes)))
          .thenReturn(result(List.of(feature)));
      doThrow(new IllegalStateException("observer failed"))
          .when(mappingObservability)
          .observe(any(), any());
      doThrow(new IllegalStateException("reporter failed"))
          .when(observationFailureReporter)
          .report(any());

      handler.handle(List.of(record));

      verify(producer).sendAll(any(Collection.class));
      verifyNoInteractions(rawDltProducer);
    }

    @Test
    void handle_rawBytesNoMapper_sendsToDltWhenSourceResolvedDecodeFails() {
      var sourceId = UUID.randomUUID();
      var objectId = UUID.randomUUID();
      var rawBytes = new byte[] {0x06, 0x0e, 0x2b, 0x34};

      // No CONTENT_KIND header, no MAPPER_ID header — Processor resolves the mapper from source.
      var consumerRecord =
          new ConsumerRecord<String, byte[]>(
              "opencop.event.external.raw", 0, 0L, "klv-key", rawBytes);
      consumerRecord
          .headers()
          .add(
              OpencopKafkaHeaders.SOURCE_ID.getName(),
              sourceId.toString().getBytes(StandardCharsets.UTF_8))
          .add(
              OpencopKafkaHeaders.OBJECT_ID.getName(),
              objectId.toString().getBytes(StandardCharsets.UTF_8));

      when(registry.decodeAndMapRealTimeResult(
              eq(objectId), any(MappingProperties.class), eq(rawBytes)))
          .thenThrow(new IllegalArgumentException("KLV mapping unsupported"));

      handler.handle(List.of(consumerRecord));

      verifyNoInteractions(blobClient);
      verifyNoInteractions(producer);
      verify(registry)
          .decodeAndMapRealTimeResult(eq(objectId), any(MappingProperties.class), eq(rawBytes));
      var attemptCaptor = ArgumentCaptor.forClass(RealtimeMappingAttempt.class);
      verify(mappingObservability).observeThrown(attemptCaptor.capture());
      var attempt = attemptCaptor.getValue();
      assertEquals(RealtimeMappingEntry.RAW_REALTIME, attempt.ingress());
      assertEquals(MappingResourceKind.DATA_SOURCE, attempt.resource().kind());
      assertEquals(sourceId, attempt.resource().id());
      assertEquals(0, attempt.partition());
      assertEquals(0L, attempt.offset());
      verify(mappingObservability, never()).observe(any(), any());
      var dltCaptor = byteBatchCaptor();
      verify(rawDltProducer, times(1)).sendAll(dltCaptor.capture());

      BrokerMessage<byte[]> dltRecord = onlyMessage(dltCaptor.getValue());
      assertArrayEquals(consumerRecord.value(), dltRecord.event());
      assertEquals("klv-key", dltRecord.key());
      assertEquals(consumerRecord.topic(), dltRecord.headers().get("kafka_dlt-original-topic"));
      assertEquals("0", dltRecord.headers().get("kafka_dlt-original-partition"));
      assertEquals("0", dltRecord.headers().get("kafka_dlt-original-offset"));
      assertEquals(
          IllegalArgumentException.class.getName(),
          dltRecord.headers().get("kafka_dlt-exception-fqcn"));
      assertEquals(
          "KLV mapping unsupported", dltRecord.headers().get("kafka_dlt-exception-message"));
      assertEquals("RAW", dltRecord.headers().get(DeadLetterHeaders.STAGE));
      assertEquals("CONTROL_UNSUPPORTED", dltRecord.headers().get(DeadLetterHeaders.FAILURE_KIND));
    }

    @Test
    void handle_dropsRecordFromWriteOnlySourceWithoutDltOrTelemetry() {
      var writeOnlySourceId = UUID.randomUUID();
      var readSourceId = UUID.randomUUID();
      var droppedObjectId = UUID.randomUUID();
      var validObjectId = UUID.randomUUID();
      var droppedBytes = new byte[] {1};
      var validBytes = new byte[] {2};
      var feature = new Feature();
      var dropped =
          rawRecord(
              "opencop.event.external.raw",
              0,
              0L,
              "dropped",
              droppedBytes,
              writeOnlySourceId,
              droppedObjectId);
      var valid =
          rawRecord(
              "opencop.event.external.raw",
              0,
              1L,
              "valid",
              validBytes,
              readSourceId,
              validObjectId);
      when(registry.decodeAndMapRealTimeResult(
              eq(droppedObjectId), any(MappingProperties.class), eq(droppedBytes)))
          .thenThrow(new WriteOnlySourceException(writeOnlySourceId));
      when(registry.decodeAndMapRealTimeResult(
              eq(validObjectId), any(MappingProperties.class), eq(validBytes)))
          .thenReturn(result(List.of(feature)));

      handler.handle(List.of(dropped, valid));

      verifyNoInteractions(rawDltProducer);
      verify(mappingObservability, never()).observeThrown(any());
      var featureCaptor = featureBatchCaptor();
      verify(producer).sendAll(featureCaptor.capture());
      assertEquals(List.of(feature), events(featureCaptor.getValue()));
    }

    @Test
    void handle_thrownDecodeTelemetryFailureDoesNotBlockDltRouting() {
      var sourceId = UUID.randomUUID();
      var objectId = UUID.randomUUID();
      var rawBytes = new byte[] {1};
      var consumerRecord =
          rawRecord("opencop.event.external.raw", 0, 0L, null, rawBytes, sourceId, objectId);
      when(registry.decodeAndMapRealTimeResult(
              eq(objectId), any(MappingProperties.class), eq(rawBytes)))
          .thenThrow(new IllegalArgumentException("bad raw"));
      doThrow(new IllegalStateException("observer failed"))
          .when(mappingObservability)
          .observeThrown(any());
      doThrow(new IllegalStateException("reporter failed"))
          .when(observationFailureReporter)
          .report(any());

      handler.handle(List.of(consumerRecord));

      verify(rawDltProducer).sendAll(any(Collection.class));
      verifyNoInteractions(producer);
    }

    @Test
    void handle_publishesRawDltBeforeMappedFeaturesForMixedBatch() {
      var sourceId = UUID.randomUUID();
      var failedObjectId = UUID.randomUUID();
      var validObjectId = UUID.randomUUID();
      var failedBytes = new byte[] {1};
      var validBytes = new byte[] {2};
      var feature = new Feature();
      var failed =
          rawRecord(
              "opencop.event.external.raw", 0, 0L, "failed", failedBytes, sourceId, failedObjectId);
      var valid =
          rawRecord(
              "opencop.event.external.raw", 0, 1L, "valid", validBytes, sourceId, validObjectId);
      when(registry.decodeAndMapRealTimeResult(
              eq(failedObjectId), any(MappingProperties.class), eq(failedBytes)))
          .thenThrow(new IllegalArgumentException("bad raw"));
      when(registry.decodeAndMapRealTimeResult(
              eq(validObjectId), any(MappingProperties.class), eq(validBytes)))
          .thenReturn(result(List.of(feature)));

      handler.handle(List.of(failed, valid));

      InOrder order = inOrder(rawDltProducer, producer);
      order.verify(rawDltProducer).sendAll(any(Collection.class));
      order.verify(producer).sendAll(any(Collection.class));
    }

    @Test
    void handle_routesFeatureSerializationFailureToRawDltWithoutBlockingValidRecords() {
      var sourceId = UUID.randomUUID();
      var failedObjectId = UUID.randomUUID();
      var validObjectId = UUID.randomUUID();
      var failedBytes = new byte[] {1};
      var validBytes = new byte[] {2};
      var failedFeature = new Feature();
      var recursiveProperty = new LinkedHashMap<String, Object>();
      recursiveProperty.put("self", recursiveProperty);
      failedFeature.setProperty("bad", recursiveProperty);
      var validFeature = new Feature();
      var failed =
          rawRecord(
              "opencop.event.external.raw",
              3,
              29L,
              "failed",
              failedBytes,
              sourceId,
              failedObjectId);
      var valid =
          rawRecord(
              "opencop.event.external.raw", 3, 30L, "valid", validBytes, sourceId, validObjectId);
      var failedResult = result(List.of(failedFeature));
      var validResult = result(List.of(validFeature));
      when(registry.decodeAndMapRealTimeResult(
              eq(failedObjectId), any(MappingProperties.class), eq(failedBytes)))
          .thenReturn(failedResult);
      when(registry.decodeAndMapRealTimeResult(
              eq(validObjectId), any(MappingProperties.class), eq(validBytes)))
          .thenReturn(validResult);
      handler =
          new MapRawToGeoHandler(
              registry,
              producer,
              new ObjectMapper(),
              blobClient,
              rawDltProducer,
              mappingObservability,
              observationFailureReporter);

      handler.handle(List.of(failed, valid));

      var dltCaptor = byteBatchCaptor();
      var mappedCaptor = featureBatchCaptor();
      var attemptCaptor = ArgumentCaptor.forClass(RealtimeMappingAttempt.class);
      InOrder order = inOrder(mappingObservability, rawDltProducer, producer);
      order.verify(mappingObservability).observe(attemptCaptor.capture(), eq(failedResult));
      order.verify(rawDltProducer).sendAll(dltCaptor.capture());
      order.verify(producer).sendAll(mappedCaptor.capture());
      var attempt = attemptCaptor.getValue();
      assertEquals(RealtimeMappingEntry.RAW_REALTIME, attempt.ingress());
      assertEquals(MappingResourceKind.DATA_SOURCE, attempt.resource().kind());
      assertEquals(sourceId, attempt.resource().id());
      assertEquals(3, attempt.partition());
      assertEquals(29L, attempt.offset());
      BrokerMessage<byte[]> dltRecord = onlyMessage(dltCaptor.getValue());
      assertArrayEquals(failed.value(), dltRecord.event());
      assertEquals("failed", dltRecord.key());
      assertEquals(List.of(validFeature), events(mappedCaptor.getValue()));
    }

    @Test
    void handle_routesInvalidDecoderDiagnosticToRawDltAfterObservation() {
      var sourceId = UUID.randomUUID();
      var objectId = UUID.randomUUID();
      var payload = new byte[] {1};
      var record =
          rawRecord("opencop.event.external.raw", 3, 29L, "invalid", payload, sourceId, objectId);
      var result = invalidDecoderResult();
      when(registry.decodeAndMapRealTimeResult(
              eq(objectId), any(MappingProperties.class), eq(payload)))
          .thenReturn(result);

      handler.handle(List.of(record));

      verify(mappingObservability).observe(any(RealtimeMappingAttempt.class), eq(result));
      verify(rawDltProducer).sendAll(any(Collection.class));
      verifyNoInteractions(producer);
    }

    @Test
    void handle_throwsWhenRawDltPublishingThrowsAndDoesNotPublishMappedFeatures() {
      var sourceId = UUID.randomUUID();
      var failedObjectId = UUID.randomUUID();
      var validObjectId = UUID.randomUUID();
      var failedBytes = new byte[] {1};
      var validBytes = new byte[] {2};
      var feature = new Feature();
      var failed =
          rawRecord(
              "opencop.event.external.raw", 0, 0L, "failed", failedBytes, sourceId, failedObjectId);
      var valid =
          rawRecord(
              "opencop.event.external.raw", 0, 1L, "valid", validBytes, sourceId, validObjectId);
      when(registry.decodeAndMapRealTimeResult(
              eq(failedObjectId), any(MappingProperties.class), eq(failedBytes)))
          .thenThrow(new IllegalArgumentException("bad raw"));
      when(registry.decodeAndMapRealTimeResult(
              eq(validObjectId), any(MappingProperties.class), eq(validBytes)))
          .thenReturn(result(List.of(feature)));
      doThrow(new IllegalStateException("dlt publish failed"))
          .when(rawDltProducer)
          .sendAll(any(Collection.class));

      assertThrows(IllegalStateException.class, () -> handler.handle(List.of(failed, valid)));

      verify(rawDltProducer).sendAll(any(Collection.class));
      verify(producer, never()).sendAll(any(Collection.class));
    }

    @Test
    void handle_throwsWhenMappedFeaturePublishingThrows() {
      var sourceId = UUID.randomUUID();
      var objectId = UUID.randomUUID();
      var rawBytes = new byte[] {10};
      var feature = new Feature();
      var record =
          rawRecord("opencop.event.external.raw", 0, 0L, "key", rawBytes, sourceId, objectId);
      when(registry.decodeAndMapRealTimeResult(
              eq(objectId), any(MappingProperties.class), eq(rawBytes)))
          .thenReturn(result(List.of(feature)));
      doThrow(new IllegalStateException("mapped publish failed"))
          .when(producer)
          .sendAll(any(Collection.class));

      assertThrows(IllegalStateException.class, () -> handler.handle(List.of(record)));

      verifyNoInteractions(rawDltProducer);
      verify(producer).sendAll(any(Collection.class));
    }

    @Test
    void handle_importEnvelopeBlobDownloadFailureThrowsWithoutRawDltPublish() throws Exception {
      var sourceId = UUID.randomUUID();
      var objectId = UUID.randomUUID();
      var mapperId = UUID.randomUUID();
      var importId = UUID.randomUUID();
      var blobKey = "imports/missing.csv";
      var envelopeJson = "{\"blobKey\":\"" + blobKey + "\",\"filename\":\"missing.csv\"}";
      var consumerRecord =
          importRecord(
              sourceId,
              objectId,
              mapperId,
              importId,
              envelopeJson.getBytes(StandardCharsets.UTF_8));
      var envelope = new ImportEnvelope(blobKey, "missing.csv", null, null);
      when(objectMapper.readValue(consumerRecord.value(), ImportEnvelope.class))
          .thenReturn(envelope);
      when(blobClient.download(blobKey)).thenThrow(new IllegalStateException("blob unavailable"));

      assertThrows(RuntimeException.class, () -> handler.handle(List.of(consumerRecord)));

      verifyNoInteractions(rawDltProducer);
      verifyNoInteractions(producer);
    }

    // The mapperId-with-raw-bytes path (CONTENT_KIND absent, MAPPER_ID present) has no active
    // producer. MAPPER_ID remains a decoder hint for raw byte flows. If a producer is added for
    // this path, verify registry.decodeAndMap(mapperId, objectId, props, message.value()) receives
    // the raw message bytes without downloading a blob.
  }

  private static Feature featureWithMediaHref(UUID mediaId) {
    OpenCopProperties props = new OpenCopProperties();

    Element element = new MediaIconElement.Builder().href("opencop:media:" + mediaId).build();
    props.setElement(element);
    Feature feature = new Feature();
    feature.setProperty("opencop", props);
    return feature;
  }

  private static Feature featureWithSymbol(String symbol) {
    OpenCopProperties props = new OpenCopProperties();

    Element element = new Element.Builder().type(ElementTypes.SYMBOL).put("symbol", symbol).build();
    props.setElement(element);
    Feature feature = new Feature();
    feature.setProperty("opencop", props);
    return feature;
  }

  private static ListAppender<ILoggingEvent> attachAppender() {
    Logger logger = (Logger) LoggerFactory.getLogger(MapRawToGeoHandler.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    return appender;
  }

  private static void detachAppender(ListAppender<ILoggingEvent> appender) {
    Logger logger = (Logger) LoggerFactory.getLogger(MapRawToGeoHandler.class);
    logger.detachAppender(appender);
    appender.stop();
  }

  private static ConsumerRecord<String, byte[]> rawRecord(
      String topic,
      int partition,
      long offset,
      String key,
      byte[] value,
      UUID sourceId,
      UUID objectId) {
    var record = new ConsumerRecord<String, byte[]>(topic, partition, offset, key, value);
    record
        .headers()
        .add(
            OpencopKafkaHeaders.SOURCE_ID.getName(),
            sourceId.toString().getBytes(StandardCharsets.UTF_8))
        .add(
            OpencopKafkaHeaders.OBJECT_ID.getName(),
            objectId.toString().getBytes(StandardCharsets.UTF_8));
    return record;
  }

  private static ConsumerRecord<String, byte[]> importRecord(
      UUID sourceId, UUID objectId, UUID mapperId, UUID importId, byte[] value) {
    var record =
        rawRecord("opencop.event.external.raw", 0, 0L, "import-key", value, sourceId, objectId);
    record
        .headers()
        .add(
            OpencopKafkaHeaders.IMPORT_ID.getName(),
            importId.toString().getBytes(StandardCharsets.UTF_8))
        .add(
            OpencopKafkaHeaders.MAPPER_ID.getName(),
            mapperId.toString().getBytes(StandardCharsets.UTF_8))
        .add(
            OpencopKafkaHeaders.CONTENT_KIND.getName(),
            ImportEnvelope.CONTENT_KIND.getBytes(StandardCharsets.UTF_8));
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

  private static <T> BrokerMessage<T> onlyMessage(Collection<BrokerMessage<T>> messages) {
    assertEquals(1, messages.size());
    return messages.iterator().next();
  }

  private static <T> List<T> events(Collection<BrokerMessage<T>> messages) {
    return messages.stream().map(BrokerMessage::event).toList();
  }

  private static DecodedMappingResult result(List<Feature> features) {
    return DecodedMappingResult.fromFeatures(UUID.randomUUID(), 1, "test", features);
  }

  private static DecodedMappingResult failedResult() {
    var mapperId = UUID.randomUUID();
    return DecodedMappingResult.fromMappingResponse(
        mapperId,
        1,
        "dynamic",
        new MappingResponse(
            mapperId,
            null,
            List.of(),
            List.of(new MappingError(null, "ON_MISSING_ERROR", "ignored", List.of(), null)),
            List.of()));
  }

  private static DecodedMappingResult invalidDecoderResult() {
    return DecodedMappingResult.invalidMapping(
        UUID.randomUUID(),
        1,
        "FIXTURE",
        new MappingDiagnostic(
            "INVALID_FIXTURE_MAPPING",
            MappingDiagnosticSeverity.ERROR,
            "Fixture requires a point",
            Map.of("field", "point")));
  }

  private static MapperProfile mapperProfile(PayloadIngressMapper<?> mapper) {
    return MapperProfile.builder()
        .withId(mapper.id())
        .withName(mapper.name())
        .withDescription(mapper.description())
        .withEditable(false)
        .withIntegrationId(PLUGIN_INTEGRATION_ID)
        .withVersion(mapper.version())
        .withArchived(false)
        .build();
  }

  @Configuration(proxyBeanMethods = false)
  @EnableIntegrationCapabilities(
      supported = INGRESS_MAPPING,
      rejected = {
        EGRESS_MAPPING,
        CONNECTION_MAPPING,
        INTEGRATION_IDENTITY,
        CONNECTION_SCHEMA,
        CONNECTION_SETTINGS,
        CONNECTION_BEHAVIOR,
        CONNECTION_INPUT,
        GEOCODING,
        CONNECTION_EXTENSION
      })
  @ComponentScan(
      basePackageClasses = EventDecoderRegistry.class,
      useDefaultFilters = false,
      includeFilters =
          @ComponentScan.Filter(
              type = FilterType.ASSIGNABLE_TYPE,
              classes = EventDecoderRegistry.class))
  static class IngressRegistryConfig {

    @Bean
    SelectedPluginMapper selectedPluginMapper() {
      return new SelectedPluginMapper();
    }

    @Bean
    IntegrationIngressMappers selectedPluginMappers(SelectedPluginMapper selectedPluginMapper) {
      return new IntegrationIngressMappers(
          new TestIntegration(PLUGIN_INTEGRATION_ID, "Test integration", "Test integration"),
          List.of(selectedPluginMapper));
    }

    @Bean
    MapperProfileService mapperProfileService() {
      return mock(MapperProfileService.class);
    }

    @Bean
    MapperFacade mapperFacade() {
      return mock(MapperFacade.class);
    }
  }
}
