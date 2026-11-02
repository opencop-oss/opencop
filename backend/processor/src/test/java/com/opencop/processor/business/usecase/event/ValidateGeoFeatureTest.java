// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.opencop.core.sdk.ports.gateway.broker.BrokerMessage;
import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.Timestampz;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import com.opencop.processor.business.model.event.EventEnvelope;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.geojson.Feature;
import org.geojson.Point;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ValidateGeoFeatureTest {

  @SuppressWarnings("unchecked")
  private final MessageBrokerProducer<Feature> producer = mock(MessageBrokerProducer.class);

  private ValidateGeoFeature validator;

  @BeforeEach
  void setUp() {
    validator = new ValidateGeoFeature(producer, true);
  }

  @Test
  void validate_withProtocolSpecificAccuracyAttribute_keepsGenericGeoJsonAtOrigin() {
    var feature = featureAtPosition("9999999.0", 0.0, 0.0);
    validator.validate(List.of(EventEnvelope.of(feature)));
    assertEquals(List.of(feature), publishedFeatures());
  }

  @Test
  void validate_passesEventWithNoCeAttribute() {
    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put("type", "fixture");
    Feature feature = featureWithAttributes(attributes, 40.0, -100.0);

    validator.validate(List.of(EventEnvelope.of(feature)));

    assertEquals(List.of(feature), publishedFeatures());
  }

  @Test
  void validate_passesEventWithNoOpencop() {
    Feature feature = new Feature();

    validator.validate(List.of(EventEnvelope.of(feature)));

    assertEquals(List.of(feature), publishedFeatures());
  }

  @Test
  void validate_doesNotPublishEmptyInput() {
    validator.validate(List.of());

    verify(producer, never()).sendAll(any());
  }

  @Test
  void validate_defaultsMissingStartAtBeforePublishingToValidatedTopic() {
    long time = System.currentTimeMillis() - 1_000;
    Feature feature =
        OpenCopPropertiesParser.writeToFeature(
            new OpenCopProperties.Builder()
                .id(UUID.randomUUID())
                .objectId("event-1")
                .source(UUID.randomUUID())
                .kind("track")
                .timestampz(new Timestampz(time, 0, time + 60_000))
                .build(),
            new Point(-100.0, 40.0));

    validator.validate(List.of(EventEnvelope.of(feature)));

    var properties = OpenCopPropertiesParser.readFromFeature(feature);
    assertEquals(List.of(feature), publishedFeatures());
    assertEquals(time, properties.getTimestampz().getStart());
  }

  @Test
  void validate_forwardsEnvelopeMetadataOnRepublish() {
    Feature feature = featureAtPosition("6.7", 40.0, -100.0);
    var metadata = Map.of("opencop-provenance-ingress-id", "topic:0:99");

    validator.validate(List.of(new EventEnvelope<>(feature, metadata)));

    assertEquals(List.of(metadata), publishedHeaders());
  }

  @Test
  void validate_multipleFeatures_preservesEachEnvelopeMetadata() {
    var first = featureAtPosition("6.7", 40.0, -100.0);
    var second = featureAtPosition("9999999.0", 0.0, 0.0);
    var firstMetadata = Map.of("opencop-provenance-ingress-id", "first");
    var secondMetadata = Map.of("opencop-provenance-ingress-id", "second");
    validator.validate(
        List.of(
            new EventEnvelope<>(first, firstMetadata),
            new EventEnvelope<>(second, secondMetadata)));
    assertEquals(List.of(firstMetadata, secondMetadata), publishedHeaders());
  }

  private static Feature featureAtPosition(String ce, double lat, double lon) {
    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put("type", "fixture");
    attributes.put("ce", ce);
    return featureWithAttributes(attributes, lat, lon);
  }

  private static Feature featureWithAttributes(
      Map<String, String> attributes, double lat, double lon) {
    Map<String, Object> opencop = new LinkedHashMap<>();
    opencop.put("attributes", attributes);
    Feature feature = new Feature();
    feature.setProperty("opencop", opencop);
    feature.setGeometry(new Point(lon, lat));
    return feature;
  }

  @Test
  void validate_keysPublishesByCanonicalSourceObjectIdentity() {
    var source = UUID.randomUUID();
    Feature feature = featureWithIdentity(source, "event-1");

    validator.validate(List.of(EventEnvelope.of(feature)));

    assertEquals(source + ":event-1", publishedKeys().get(0));
  }

  @Test
  void validate_publishesUnkeyedWhenFeatureLacksStableIdentity() {
    Feature feature = featureAtPosition("6.7", 40.0, -100.0);

    validator.validate(List.of(EventEnvelope.of(feature)));

    assertNull(publishedKeys().get(0));
  }

  @Test
  void validate_killSwitchPublishesUnkeyedDespiteIdentity() {
    var unkeyed = new ValidateGeoFeature(producer, false);
    Feature feature = featureWithIdentity(UUID.randomUUID(), "event-1");

    unkeyed.validate(List.of(EventEnvelope.of(feature)));

    assertNull(publishedKeys().get(0));
  }

  private static Feature featureWithIdentity(UUID source, String objectId) {
    long time = System.currentTimeMillis() - 1_000;
    return OpenCopPropertiesParser.writeToFeature(
        new OpenCopProperties.Builder()
            .id(UUID.randomUUID())
            .objectId(objectId)
            .source(source)
            .kind("track")
            .timestampz(new Timestampz(time, 0, time + 60_000))
            .build(),
        new Point(-100.0, 40.0));
  }

  private List<Feature> publishedFeatures() {
    var captor = brokerMessageBatchCaptor();
    verify(producer).sendAll(captor.capture());
    return captor.getValue().stream().map(BrokerMessage::event).toList();
  }

  private List<Map<String, String>> publishedHeaders() {
    var captor = brokerMessageBatchCaptor();
    verify(producer).sendAll(captor.capture());
    return captor.getValue().stream().map(BrokerMessage::headers).toList();
  }

  private List<String> publishedKeys() {
    var captor = brokerMessageBatchCaptor();
    verify(producer).sendAll(captor.capture());
    return captor.getValue().stream().map(BrokerMessage::key).toList();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static ArgumentCaptor<Collection<BrokerMessage<Feature>>> brokerMessageBatchCaptor() {
    return ArgumentCaptor.forClass(Collection.class);
  }
}
