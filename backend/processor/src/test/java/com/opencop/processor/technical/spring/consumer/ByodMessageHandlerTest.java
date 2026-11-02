// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.consumer;

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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.business.model.integration.mapping.ingress.IntegrationIngressMappers;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.model.mapper.MappingError;
import com.opencop.core.sdk.model.mapper.MappingResponse;
import com.opencop.core.sdk.ports.gateway.broker.BrokerMessage;
import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.DecodedMappingResult;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.InvalidMappingDiagnosticException;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.MappingDiagnostic;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.MappingDiagnosticSeverity;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.PayloadIngressMapper;
import com.opencop.core.sdk.technical.config.FrameworkCompatibleConfigurationBeanNameGenerator;
import com.opencop.core.sdk.technical.config.integration.EnableIntegrationCapabilities;
import com.opencop.core.sdk.technical.gateway.mapping.ingress.EventDecoderRegistry;
import com.opencop.core.sdk.technical.mapper.MapperFacade;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema.TestIntegration;
import com.opencop.processor.business.model.mapping.MappingResourceKind;
import com.opencop.processor.business.model.mapping.RealtimeMappingAttempt;
import com.opencop.processor.business.model.mapping.RealtimeMappingEntry;
import com.opencop.processor.technical.observability.mapping.MappingObservationFailureReporter;
import com.opencop.processor.technical.observability.mapping.RealtimeMappingOutcomeObservability;
import com.opencop.processor.technical.spring.config.PipelinePublishProperties;
import com.opencop.processor.testfixture.mapping.SelectedPluginMapper;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.geojson.Feature;
import org.geojson.Point;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

@SuppressWarnings("unchecked")
class ByodMessageHandlerTest {

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

  private EventDecoderRegistry registry;
  private MessageBrokerProducer<Feature> producer;
  private ByodRuntimeObservationCoalescer runtimeObservationCoalescer;
  private RealtimeMappingOutcomeObservability mappingObservability;
  private MappingObservationFailureReporter observationFailureReporter;
  private ByodMessageHandler handler;

  @BeforeEach
  void setUp() {
    registry = mock(EventDecoderRegistry.class);
    producer = mock(MessageBrokerProducer.class);
    runtimeObservationCoalescer = mock(ByodRuntimeObservationCoalescer.class);
    mappingObservability = mock(RealtimeMappingOutcomeObservability.class);
    observationFailureReporter = mock(MappingObservationFailureReporter.class);
    handler = handler(true);
  }

  private ByodMessageHandler handler(boolean keyedPublish) {
    var publishProperties = new PipelinePublishProperties();
    publishProperties.setKeyed(keyedPublish);
    return new ByodMessageHandler(
        registry,
        producer,
        runtimeObservationCoalescer,
        mappingObservability,
        observationFailureReporter,
        publishProperties);
  }

  @Test
  void onMessage_decodesAndSendsFeatures() {
    var sourceId = UUID.fromString("2db183bf-1a15-4569-b471-47ed8ad2a1fe");
    var topic = "opencop.byod.test." + sourceId;
    var payload = new byte[] {1, 2, 3};
    var feature1 = new Feature();
    var feature2 = new Feature();
    when(registry.decodeAndMapRealTimeResult(
            any(UUID.class), any(MappingProperties.class), eq(payload)))
        .thenReturn(result(List.of(feature1, feature2)));

    handler.onMessage(new ConsumerRecord<>(topic, 0, 0L, null, payload));

    var captor = brokerMessageBatchCaptor();
    verify(producer).sendAll(captor.capture());
    assertEquals(List.of(feature1, feature2), events(captor.getValue()));
  }

  @Test
  void onMessage_keysMappedPublishesByCanonicalIdentity() {
    var sourceId = UUID.fromString("2db183bf-1a15-4569-b471-47ed8ad2a1fe");
    var topic = "opencop.byod.test." + sourceId;
    var mapped = mappedFeature(sourceId, "tracked-1");
    when(registry.decodeAndMapRealTimeResult(any(UUID.class), any(MappingProperties.class), any()))
        .thenReturn(result(List.of(mapped)));

    handler.onMessage(new ConsumerRecord<>(topic, 0, 0L, null, new byte[0]));

    var captor = brokerMessageBatchCaptor();
    verify(producer).sendAll(captor.capture());
    assertEquals(sourceId + ":tracked-1", captor.getValue().iterator().next().key());
  }

  @Test
  void onMessage_killSwitchPublishesUnkeyedDespiteIdentity() {
    var sourceId = UUID.fromString("2db183bf-1a15-4569-b471-47ed8ad2a1fe");
    var topic = "opencop.byod.test." + sourceId;
    var mapped = mappedFeature(sourceId, "tracked-1");
    when(registry.decodeAndMapRealTimeResult(any(UUID.class), any(MappingProperties.class), any()))
        .thenReturn(result(List.of(mapped)));

    handler(false).onMessage(new ConsumerRecord<>(topic, 0, 0L, null, new byte[0]));

    var captor = brokerMessageBatchCaptor();
    verify(producer).sendAll(captor.capture());
    assertNull(captor.getValue().iterator().next().key());
  }

  @Test
  void onMessage_malformedOpencopNodePublishesUnkeyedInsteadOfFailing() {
    var sourceId = UUID.fromString("2db183bf-1a15-4569-b471-47ed8ad2a1fe");
    var topic = "opencop.byod.test." + sourceId;
    // GeoJSON passthrough attaches the producer's raw map; a non-UUID source must not make key
    // derivation throw on the ingest path — the feature publishes unkeyed and dies at validation.
    var passthrough = new Feature();
    passthrough.setProperty("opencop", java.util.Map.of("source", "not-a-uuid"));
    when(registry.decodeAndMapRealTimeResult(any(UUID.class), any(MappingProperties.class), any()))
        .thenReturn(result(List.of(passthrough)));

    handler.onMessage(new ConsumerRecord<>(topic, 0, 0L, null, new byte[0]));

    var captor = brokerMessageBatchCaptor();
    verify(producer).sendAll(captor.capture());
    assertNull(captor.getValue().iterator().next().key());
  }

  @Test
  void onMessage_publishesUnkeyedWhenMappedFeatureLacksStableIdentity() {
    var sourceId = UUID.fromString("2db183bf-1a15-4569-b471-47ed8ad2a1fe");
    var topic = "opencop.byod.test." + sourceId;
    when(registry.decodeAndMapRealTimeResult(any(UUID.class), any(MappingProperties.class), any()))
        .thenReturn(result(List.of(new Feature())));

    handler.onMessage(new ConsumerRecord<>(topic, 0, 0L, null, new byte[0]));

    var captor = brokerMessageBatchCaptor();
    verify(producer).sendAll(captor.capture());
    assertNull(captor.getValue().iterator().next().key());
  }

  private static Feature mappedFeature(UUID sourceId, String objectId) {
    return OpenCopPropertiesParser.writeToFeature(
        new OpenCopProperties.Builder().objectId(objectId).source(sourceId).build(),
        new Point(-98.0, 39.0));
  }

  @Test
  void onMessage_passesCorrectSourceIdToDecoder() {
    var sourceId = UUID.fromString("2db183bf-1a15-4569-b471-47ed8ad2a1fe");
    var topic = "opencop.byod.test." + sourceId;
    when(registry.decodeAndMapRealTimeResult(any(UUID.class), any(MappingProperties.class), any()))
        .thenReturn(result(List.of()));

    handler.onMessage(new ConsumerRecord<>(topic, 0, 0L, null, new byte[0]));

    var captor = ArgumentCaptor.forClass(MappingProperties.class);
    verify(registry).decodeAndMapRealTimeResult(any(UUID.class), captor.capture(), any());
    assertEquals(sourceId, captor.getValue().sourceId());
  }

  @Test
  void onMessage_recordsRuntimeObservationAfterSuccessfulProcessing() {
    var sourceId = UUID.fromString("2db183bf-1a15-4569-b471-47ed8ad2a1fe");
    var topic = "opencop.byod.test." + sourceId;
    when(registry.decodeAndMapRealTimeResult(any(UUID.class), any(MappingProperties.class), any()))
        .thenReturn(result(List.of()));

    handler.onMessage(new ConsumerRecord<>(topic, 0, 0L, null, new byte[0]));

    verify(runtimeObservationCoalescer).recordSuccess(eq(sourceId), any(), any());
    verify(mappingObservability).observe(any(RealtimeMappingAttempt.class), any());
  }

  @Test
  void onMessage_recordsTheMessagesOwnKafkaCoordinatesAsItsToken() {
    var sourceId = UUID.fromString("2db183bf-1a15-4569-b471-47ed8ad2a1fe");
    var topic = "opencop.byod.test." + sourceId;
    when(registry.decodeAndMapRealTimeResult(any(UUID.class), any(MappingProperties.class), any()))
        .thenReturn(result(List.of()));

    handler.onMessage(new ConsumerRecord<>(topic, 3, 4711L, null, new byte[0]));

    // topic:partition:offset is the message's permanent address: a redelivery carries the same
    // one, so replaying it cannot promote fresh activity for the source.
    var tokenCaptor = ArgumentCaptor.forClass(String.class);
    verify(runtimeObservationCoalescer).recordSuccess(eq(sourceId), any(), tokenCaptor.capture());
    assertEquals(topic + ":3:4711", tokenCaptor.getValue());
  }

  @Test
  void onMessage_structuredFailureObservesButPublishesNothingAndStillRecordsRuntimeSuccess() {
    var sourceId = UUID.randomUUID();
    var payload = new byte[] {1};
    var result = failedResult();
    when(registry.decodeAndMapRealTimeResult(
            any(UUID.class), any(MappingProperties.class), eq(payload)))
        .thenReturn(result);

    handler.onMessage(new ConsumerRecord<>("opencop.byod.test." + sourceId, 1, 9L, null, payload));

    verify(mappingObservability).observe(any(RealtimeMappingAttempt.class), eq(result));
    verify(mappingObservability, never()).observeThrown(any());
    verify(producer, never()).sendAll(any());
    verify(runtimeObservationCoalescer).recordSuccess(eq(sourceId), any(), any());
  }

  @Test
  void onMessage_invalidDecoderDiagnosticObservesThenPropagatesForRetryAndDlt() {
    var sourceId = UUID.randomUUID();
    var payload = new byte[] {1};
    var result = invalidDecoderResult();
    when(registry.decodeAndMapRealTimeResult(
            any(UUID.class), any(MappingProperties.class), eq(payload)))
        .thenReturn(result);

    var failure =
        assertThrows(
            InvalidMappingDiagnosticException.class,
            () ->
                handler.onMessage(
                    new ConsumerRecord<>("opencop.byod.test." + sourceId, 1, 9L, null, payload)));

    assertEquals("point", failure.diagnosticField());
    assertEquals("Decoder rejected mapping field=point", failure.getMessage());
    verify(mappingObservability).observe(any(RealtimeMappingAttempt.class), eq(result));
    verify(mappingObservability, never()).observeThrown(any());
    verify(producer, never()).sendAll(any());
    verify(runtimeObservationCoalescer, never()).recordSuccess(any(), any(), any());
  }

  @Test
  void onMessage_executesTheSelectedPluginMapperAndReportsItsLocalVersion() {
    var sourceId = UUID.randomUUID();
    INGRESS_REGISTRY_CONTEXT.run(
        context -> {
          var pluginMapper = context.getBean(SelectedPluginMapper.class);
          var persistedMapper =
              mapperProfile(pluginMapper).toBuilder()
                  .withIntegrationId(PLUGIN_INTEGRATION_ID)
                  .withVersion(41)
                  .build();
          when(context.getBean(MapperProfileService.class).findLatestForSource(sourceId))
              .thenReturn(Optional.of(persistedMapper));
          var actualProducer = mock(MessageBrokerProducer.class);
          var actualObservability = mock(RealtimeMappingOutcomeObservability.class);
          var actualHandler =
              new ByodMessageHandler(
                  context.getBean(EventDecoderRegistry.class),
                  actualProducer,
                  mock(ByodRuntimeObservationCoalescer.class),
                  actualObservability,
                  mock(MappingObservationFailureReporter.class),
                  new PipelinePublishProperties());

          actualHandler.onMessage(
              record("opencop.byod.test." + sourceId, "plugin payload".getBytes()));

          var messages = brokerMessageBatchCaptor();
          verify(actualProducer).sendAll(messages.capture());
          assertEquals(
              "SELECTED-PLUGIN", events(messages.getValue()).getFirst().getProperty("mappedBy"));
          assertEquals(
              "plugin payload", events(messages.getValue()).getFirst().getProperty("payload"));
          var result = ArgumentCaptor.forClass(DecodedMappingResult.class);
          verify(actualObservability).observe(any(), result.capture());
          assertEquals(pluginMapper.id(), result.getValue().mapperId());
          assertEquals(7, result.getValue().mapperVersion());
        });
  }

  @Test
  void onMessage_surfacesArchivedMapperAsUnavailableThroughTheRetryAndDltSeam() {
    var sourceId = UUID.randomUUID();
    INGRESS_REGISTRY_CONTEXT.run(
        context -> {
          var pluginMapper = context.getBean(SelectedPluginMapper.class);
          var archivedMapper =
              mapperProfile(pluginMapper).toBuilder()
                  .withIntegrationId(PLUGIN_INTEGRATION_ID)
                  .withArchived(true)
                  .build();
          when(context.getBean(MapperProfileService.class).findLatestForSource(sourceId))
              .thenReturn(Optional.of(archivedMapper));
          var actualProducer = mock(MessageBrokerProducer.class);
          var actualCoalescer = mock(ByodRuntimeObservationCoalescer.class);
          var actualObservability = mock(RealtimeMappingOutcomeObservability.class);
          var actualHandler =
              new ByodMessageHandler(
                  context.getBean(EventDecoderRegistry.class),
                  actualProducer,
                  actualCoalescer,
                  actualObservability,
                  mock(MappingObservationFailureReporter.class),
                  new PipelinePublishProperties());

          var failure =
              assertThrows(
                  IllegalStateException.class,
                  () ->
                      actualHandler.onMessage(
                          record("opencop.byod.test." + sourceId, "plugin payload".getBytes())));

          assertEquals("ingress mapper unavailable", failure.getMessage());
          verify(actualObservability).observeThrown(any());
          verify(actualObservability, never()).observe(any(), any());
          verify(actualProducer, never()).sendAll(any());
          verify(actualCoalescer, never()).recordSuccess(any(), any());
        });
  }

  @Test
  void onMessage_observerAndReporterFailureDoNotPreventPublicationOrRuntimeSuccess() {
    var sourceId = UUID.randomUUID();
    var payload = new byte[] {1};
    var feature = new Feature();
    when(registry.decodeAndMapRealTimeResult(
            any(UUID.class), any(MappingProperties.class), eq(payload)))
        .thenReturn(result(List.of(feature)));
    doThrow(new IllegalStateException("observer failed"))
        .when(mappingObservability)
        .observe(any(), any());
    doThrow(new IllegalStateException("reporter failed"))
        .when(observationFailureReporter)
        .report(any());

    handler.onMessage(new ConsumerRecord<>("opencop.byod.test." + sourceId, 0, 0L, null, payload));

    verify(producer).sendAll(any(Collection.class));
    verify(runtimeObservationCoalescer).recordSuccess(eq(sourceId), any(), any());
  }

  @Test
  void onMessage_doesNotRecordRuntimeObservationWhenProcessingFails() {
    var sourceId = UUID.fromString("2db183bf-1a15-4569-b471-47ed8ad2a1fe");
    var topic = "opencop.byod.test." + sourceId;
    var payload = new byte[] {1};
    when(registry.decodeAndMapRealTimeResult(
            any(UUID.class), any(MappingProperties.class), eq(payload)))
        .thenThrow(new IllegalStateException("decode failed"));

    assertThrows(
        IllegalStateException.class,
        () -> handler.onMessage(new ConsumerRecord<>(topic, 0, 0L, null, payload)));

    verify(runtimeObservationCoalescer, never()).recordSuccess(any(), any(), any());
  }

  @Test
  void onMessage_thrownDecodeRecordsThrownOutcomeAndPropagates() {
    var sourceId = UUID.fromString("2db183bf-1a15-4569-b471-47ed8ad2a1fe");
    var topic = "opencop.byod.test." + sourceId;
    var payload = new byte[] {1};
    var decodeFailure = new IllegalStateException("decode failed");
    when(registry.decodeAndMapRealTimeResult(
            any(UUID.class), any(MappingProperties.class), eq(payload)))
        .thenThrow(decodeFailure);

    var thrown =
        assertThrows(
            IllegalStateException.class,
            () -> handler.onMessage(new ConsumerRecord<>(topic, 3, 27L, null, payload)));

    assertSame(decodeFailure, thrown);
    var attemptCaptor = ArgumentCaptor.forClass(RealtimeMappingAttempt.class);
    verify(mappingObservability).observeThrown(attemptCaptor.capture());
    var attempt = attemptCaptor.getValue();
    assertEquals(RealtimeMappingEntry.BYOD, attempt.ingress());
    assertEquals(MappingResourceKind.DATA_SOURCE, attempt.resource().kind());
    assertEquals(sourceId, attempt.resource().id());
    assertEquals(3, attempt.partition());
    assertEquals(27L, attempt.offset());
    verify(mappingObservability, never()).observe(any(), any());
    verify(producer, never()).sendAll(any());
    verify(runtimeObservationCoalescer, never()).recordSuccess(any(), any(), any());
  }

  @Test
  void onMessage_thrownDecodeTelemetryFailureStillPropagatesOriginalException() {
    var sourceId = UUID.randomUUID();
    var payload = new byte[] {1};
    when(registry.decodeAndMapRealTimeResult(
            any(UUID.class), any(MappingProperties.class), eq(payload)))
        .thenThrow(new IllegalStateException("decode failed"));
    doThrow(new IllegalArgumentException("observer failed"))
        .when(mappingObservability)
        .observeThrown(any());
    doThrow(new IllegalArgumentException("reporter failed"))
        .when(observationFailureReporter)
        .report(any());

    assertThrows(
        IllegalStateException.class,
        () ->
            handler.onMessage(
                new ConsumerRecord<>("opencop.byod.test." + sourceId, 0, 0L, null, payload)));
  }

  @Test
  void onMessage_propagatesPublishFailureToListenerErrorHandler() {
    var sourceId = UUID.fromString("2db183bf-1a15-4569-b471-47ed8ad2a1fe");
    var topic = "opencop.byod.test." + sourceId;
    var payload = new byte[] {1};
    var feature = new Feature();
    var result = result(List.of(feature));
    when(registry.decodeAndMapRealTimeResult(
            any(UUID.class), any(MappingProperties.class), eq(payload)))
        .thenReturn(result);
    doThrow(new IllegalStateException("publish failed"))
        .when(producer)
        .sendAll(any(Collection.class));

    assertThrows(
        IllegalStateException.class,
        () -> handler.onMessage(new ConsumerRecord<>(topic, 3, 27L, null, payload)));

    var attemptCaptor = ArgumentCaptor.forClass(RealtimeMappingAttempt.class);
    var order = inOrder(mappingObservability, producer);
    order.verify(mappingObservability).observe(attemptCaptor.capture(), eq(result));
    order.verify(producer).sendAll(any(Collection.class));
    var attempt = attemptCaptor.getValue();
    assertEquals(RealtimeMappingEntry.BYOD, attempt.ingress());
    assertEquals(MappingResourceKind.DATA_SOURCE, attempt.resource().kind());
    assertEquals(sourceId, attempt.resource().id());
    assertEquals(3, attempt.partition());
    assertEquals(27L, attempt.offset());
    verify(runtimeObservationCoalescer, never()).recordSuccess(any(), any(), any());
  }

  @Test
  void extractSourceId_parsesUuidFromTopic() {
    var expected = UUID.fromString("2db183bf-1a15-4569-b471-47ed8ad2a1fe");
    assertEquals(
        expected,
        ByodMessageHandler.extractSourceId(
            "opencop.byod.foo.2db183bf-1a15-4569-b471-47ed8ad2a1fe"));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static ArgumentCaptor<Collection<BrokerMessage<Feature>>> brokerMessageBatchCaptor() {
    return ArgumentCaptor.forClass(Collection.class);
  }

  private static List<Feature> events(Collection<BrokerMessage<Feature>> messages) {
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
        "mapper",
        new MappingDiagnostic(
            "INVALID_MAPPING",
            MappingDiagnosticSeverity.ERROR,
            "Mapping requires a point",
            java.util.Map.of("field", "point")));
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

  private static ConsumerRecord<String, byte[]> record(String topic, byte[] payload) {
    return new ConsumerRecord<>(topic, 1, 9L, null, payload);
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
