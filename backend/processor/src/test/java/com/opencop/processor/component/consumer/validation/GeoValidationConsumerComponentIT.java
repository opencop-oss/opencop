// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.consumer.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.opencop.processor.business.usecase.event.ValidateGeoFeature;
import com.opencop.processor.component.consumer.realtime.RecordingFeatureProducer;
import com.opencop.processor.component.support.AbstractProcessorKafkaComponentIT;
import com.opencop.processor.component.support.ProcessorKafkaComponentTestApplication;
import com.opencop.processor.ports.business.usecase.event.ValidateEvent;
import java.time.Duration;
import java.util.Map;
import org.geojson.Feature;
import org.geojson.LngLatAlt;
import org.geojson.Point;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(
    classes = {
      ProcessorKafkaComponentTestApplication.class,
      GeoValidationConsumerComponentTestContext.class
    })
@DirtiesContext
@EmbeddedKafka(partitions = 1, topics = "${opencop.messaging-broker.listeners.validation.topic}")
class GeoValidationConsumerComponentIT extends AbstractProcessorKafkaComponentIT {

  @Autowired private KafkaTemplate<String, Feature> featureKafkaTemplate;
  @Autowired private ValidateEvent<Feature> validateEvent;
  @Autowired private RecordingFeatureProducer validatedProducer;
  @Autowired private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
  @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;

  @Value("${opencop.messaging-broker.listeners.validation.topic}")
  private String topic;

  @BeforeEach
  void beforeEach() {
    MessageListenerContainer listenerContainer =
        kafkaListenerEndpointRegistry.getListenerContainer("validationEventConsumer");
    assertNotNull(listenerContainer);
    ContainerTestUtils.waitForAssignment(
        listenerContainer, embeddedKafkaBroker.getPartitionsPerTopic());
  }

  @AfterEach
  void afterEach() {
    validatedProducer.reset();
  }

  @Nested
  class StartupAndWiring {

    @Test
    void wiresValidationConsumerToRealValidateGeoFeature() {
      assertInstanceOf(ValidateGeoFeature.class, validateEvent);
    }
  }

  @Nested
  class HappyPath {

    @Test
    void consumesMappedFeaturesWithoutProtocolSpecificFiltering() throws Exception {
      var validFeature = feature("valid-feature", 39.9526d, -75.1652d, "25");
      var unknownPositionFeature = feature("unknown-position", 0.0d, 0.0d, "9999999");

      featureKafkaTemplate.send(topic, "key-1", validFeature);
      featureKafkaTemplate.send(topic, "key-2", unknownPositionFeature);
      featureKafkaTemplate.flush();

      assertPublishedFeature(
          "valid-feature", 39.9526d, -75.1652d, validatedProducer.awaitPublished());
      assertPublishedFeature("unknown-position", 0.0d, 0.0d, validatedProducer.awaitPublished());
      assertNull(validatedProducer.pollPublished(Duration.ofMillis(500)));
    }
  }

  @Nested
  class OtherEdgeCases {

    @Test
    void keepsFeaturesWithNullIslandCoordinatesWhenCeIsMissingOrInvalid() throws Exception {
      var missingCe = featureWithoutCe("missing-ce", 0.0d, 0.0d);
      var invalidCe = feature("invalid-ce", 0.0d, 0.0d, "not-a-number");

      featureKafkaTemplate.send(topic, "key-1", missingCe);
      featureKafkaTemplate.send(topic, "key-2", invalidCe);
      featureKafkaTemplate.flush();

      assertPublishedFeature("missing-ce", 0.0d, 0.0d, validatedProducer.awaitPublished());
      assertPublishedFeature("invalid-ce", 0.0d, 0.0d, validatedProducer.awaitPublished());
      assertNull(validatedProducer.pollPublished(Duration.ofMillis(500)));
    }
  }

  private static void assertPublishedFeature(
      String expectedId, double expectedLatitude, double expectedLongitude, Feature feature) {
    assertEquals(expectedId, feature.getId());
    var point = (Point) feature.getGeometry();
    assertEquals(expectedLatitude, point.getCoordinates().getLatitude());
    assertEquals(expectedLongitude, point.getCoordinates().getLongitude());
  }

  private static Feature feature(String id, double latitude, double longitude, String ce) {
    var feature = featureWithoutCe(id, latitude, longitude);
    feature.setProperty("opencop", Map.of("attributes", Map.of("ce", ce)));
    return feature;
  }

  private static Feature featureWithoutCe(String id, double latitude, double longitude) {
    var feature = new Feature();
    feature.setId(id);
    feature.setGeometry(new Point(new LngLatAlt(longitude, latitude)));
    feature.setProperty("opencop", Map.of("attributes", Map.of()));
    return feature;
  }
}
