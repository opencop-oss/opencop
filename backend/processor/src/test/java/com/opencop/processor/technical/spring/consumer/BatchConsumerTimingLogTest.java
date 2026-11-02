// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.processor.business.usecase.event.EventAutoAttachmentService;
import com.opencop.processor.ports.business.usecase.event.RawEventHandler;
import com.opencop.processor.ports.business.usecase.event.RealTimeEventHandler;
import com.opencop.processor.ports.business.usecase.event.ValidateEvent;
import com.opencop.processor.technical.gateway.db.PostGisFeatureRecordMapper;
import com.opencop.processor.technical.gateway.model.PostGisFeatureRecord;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.geojson.Feature;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

@SuppressWarnings("unchecked")
class BatchConsumerTimingLogTest {

  @Test
  void rawIngestLogsDurationOnSuccess() {
    RawEventHandler<byte[]> eventIngest = mock(RawEventHandler.class);
    var records =
        List.of(
            new ConsumerRecord<>(
                "opencop.raw", 0, 1L, "raw-key", "payload".getBytes(StandardCharsets.UTF_8)));
    var appender = attachAppender(RawIngestConsumer.class);

    try {
      new RawIngestConsumer(eventIngest).rawEvent(records);
    } finally {
      detachAppender(RawIngestConsumer.class, appender);
    }

    verify(eventIngest).handle(records);
    assertLogged(
        appender, "Raw ingest batch processed successfully: records=1, bytes=7, durationMs=\\d+");
  }

  @Test
  void rawIngestLogsDurationOnFailure() {
    RawEventHandler<byte[]> eventIngest = mock(RawEventHandler.class);
    var records =
        List.of(
            new ConsumerRecord<>(
                "opencop.raw", 0, 1L, "raw-key", "payload".getBytes(StandardCharsets.UTF_8)));
    doThrow(new IllegalStateException("boom")).when(eventIngest).handle(records);
    var appender = attachAppender(RawIngestConsumer.class);

    try {
      assertThrows(
          IllegalStateException.class, () -> new RawIngestConsumer(eventIngest).rawEvent(records));
    } finally {
      detachAppender(RawIngestConsumer.class, appender);
    }

    assertLogged(appender, "Raw ingest batch failed: records=1, bytes=7, durationMs=\\d+");
  }

  @Test
  void realTimeLogsDurationOnSuccess() {
    RealTimeEventHandler<List<Feature>> usecase = mock(RealTimeEventHandler.class);
    var appender = attachAppender(RealTimeConsumer.class);

    try {
      new RealTimeConsumer(usecase).realTimeEvent(List.of(feature("feature-1")));
    } finally {
      detachAppender(RealTimeConsumer.class, appender);
    }

    assertLogged(appender, "Real-time batch processed successfully: count=1, durationMs=\\d+");
  }

  @Test
  void realTimeLogsDurationOnFailure() {
    RealTimeEventHandler<List<Feature>> usecase = mock(RealTimeEventHandler.class);
    doThrow(new IllegalStateException("boom")).when(usecase).process(any());
    var appender = attachAppender(RealTimeConsumer.class);

    try {
      assertThrows(
          IllegalStateException.class,
          () -> new RealTimeConsumer(usecase).realTimeEvent(List.of(feature("feature-1"))));
    } finally {
      detachAppender(RealTimeConsumer.class, appender);
    }

    assertLogged(
        appender, "Real-time batch failed: count=1, durationMs=\\d+, featureIds=\\[feature-1\\]");
  }

  @Test
  void validationLogsDurationOnSuccess() {
    ValidateEvent<Feature> usecase = mock(ValidateEvent.class);
    var appender = attachAppender(GeoValidationConsumer.class);

    try {
      new GeoValidationConsumer(usecase)
          .validate(
              List.of(
                  new ConsumerRecord<>("opencop.geo.mapped", 0, 1L, "key", feature("feature-1"))));
    } finally {
      detachAppender(GeoValidationConsumer.class, appender);
    }

    assertLogged(appender, "Validation batch processed successfully: count=1, durationMs=\\d+");
  }

  @Test
  void validationLogsDurationOnFailure() {
    ValidateEvent<Feature> usecase = mock(ValidateEvent.class);
    doThrow(new IllegalStateException("boom")).when(usecase).validate(any());
    var appender = attachAppender(GeoValidationConsumer.class);

    try {
      assertThrows(
          IllegalStateException.class,
          () ->
              new GeoValidationConsumer(usecase)
                  .validate(
                      List.of(
                          new ConsumerRecord<>(
                              "opencop.geo.mapped", 0, 1L, "key", feature("feature-1")))));
    } finally {
      detachAppender(GeoValidationConsumer.class, appender);
    }

    assertLogged(
        appender, "Validation batch failed: count=1, durationMs=\\d+, featureIds=\\[feature-1\\]");
  }

  @Test
  void persistenceLogsDurationOnSuccess() throws Exception {
    var mapper = mock(PostGisFeatureRecordMapper.class);
    var retryingPersistenceService = mock(RetryingPersistenceService.class);
    KafkaTemplate<String, Feature> kafkaTemplate = mock(KafkaTemplate.class);
    var autoAttachmentService = mock(EventAutoAttachmentService.class);
    var feature = feature("feature-1");
    when(mapper.toRecord(eq(feature), any())).thenReturn(postGisFeatureRecord());
    var appender = attachAppender(PersistenceConsumer.class);

    try {
      new PersistenceConsumer(
              mapper, retryingPersistenceService, kafkaTemplate, autoAttachmentService)
          .persist(List.of(new ConsumerRecord<>("opencop.geo.validated", 0, 1L, "key", feature)));
    } finally {
      detachAppender(PersistenceConsumer.class, appender);
    }

    assertLogged(
        appender,
        "Persistence batch processed successfully: count=1, preparedCount=1, deadLetteredCount=0, durationMs=\\d+");
  }

  @Test
  void persistenceLogsDurationOnFailure() throws Exception {
    var mapper = mock(PostGisFeatureRecordMapper.class);
    var retryingPersistenceService = mock(RetryingPersistenceService.class);
    KafkaTemplate<String, Feature> kafkaTemplate = mock(KafkaTemplate.class);
    var autoAttachmentService = mock(EventAutoAttachmentService.class);
    var feature = feature("feature-1");
    when(mapper.toRecord(eq(feature), any())).thenReturn(postGisFeatureRecord());
    doThrow(new IllegalStateException("db unavailable"))
        .when(retryingPersistenceService)
        .persist(any(), any());
    var appender = attachAppender(PersistenceConsumer.class);

    try {
      assertThrows(
          IllegalStateException.class,
          () ->
              new PersistenceConsumer(
                      mapper, retryingPersistenceService, kafkaTemplate, autoAttachmentService)
                  .persist(
                      List.of(
                          new ConsumerRecord<>("opencop.geo.validated", 0, 1L, "key", feature))));
    } finally {
      detachAppender(PersistenceConsumer.class, appender);
    }

    assertLogged(
        appender, "Persistence batch failed: count=1, durationMs=\\d+, featureIds=\\[feature-1\\]");
  }

  private static Feature feature(String id) {
    var feature = new Feature();
    feature.setId(id);
    return feature;
  }

  private static PostGisFeatureRecord postGisFeatureRecord() {
    return new PostGisFeatureRecord(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440700"),
        "TRACK",
        UUID.fromString("550e8400-e29b-41d4-a716-446655440701"),
        null,
        "obj-1",
        null,
        null,
        "{\"type\":\"Point\",\"coordinates\":[-73.5,45.5]}",
        "{\"type\":\"Feature\"}");
  }

  private static ListAppender<ILoggingEvent> attachAppender(Class<?> loggerClass) {
    Logger logger = (Logger) LoggerFactory.getLogger(loggerClass);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    return appender;
  }

  private static void detachAppender(Class<?> loggerClass, ListAppender<ILoggingEvent> appender) {
    Logger logger = (Logger) LoggerFactory.getLogger(loggerClass);
    logger.detachAppender(appender);
    appender.stop();
  }

  private static void assertLogged(ListAppender<ILoggingEvent> appender, String expectedPattern) {
    assertTrue(
        appender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .anyMatch(message -> message.matches(expectedPattern)));
  }
}
