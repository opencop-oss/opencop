// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.consumer.byod;

import static com.opencop.core.sdk.technical.gateway.broker.kafka.KafkaNaming.DLT_SUFFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.opencop.core.sdk.ports.gateway.blob.BlobClient;
import com.opencop.core.sdk.technical.gateway.mapping.ingress.EventDecoderRegistry;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import com.opencop.processor.component.consumer.realtime.RecordingFeatureProducer;
import com.opencop.processor.component.support.AbstractProcessorKafkaDatabaseIT;
import com.opencop.processor.component.support.ProcessorKafkaComponentTestApplication;
import com.opencop.processor.technical.spring.config.byod.ByodDeadLetterConsumerManager;
import com.opencop.processor.technical.spring.config.byod.ByodListenerContainerFactory;
import com.opencop.processor.technical.spring.consumer.ByodRuntimeObservationCoalescer;
import com.opencop.processor.technical.spring.dlt.DeadLetterRecord;
import com.opencop.processor.technical.spring.dlt.ProcessorDeadLetterRecordRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(
    classes = {
      ProcessorKafkaComponentTestApplication.class,
      ByodDeadLetterIndexingComponentTestContext.class
    },
    properties = {
      "opencop.messaging-broker.dlt-indexing.enabled=true",
      "opencop.byod.consumer.retry-attempts=1",
      "opencop.byod.consumer.retry-backoff-ms=0"
    })
@DirtiesContext
@EmbeddedKafka(partitions = 1)
class ByodDltPartitionRoutingComponentIT extends AbstractProcessorKafkaDatabaseIT {

  private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(25);

  @Autowired private KafkaTemplate<String, byte[]> kafkaTemplate;
  @Autowired private BlobClient blobClient;
  @Autowired private EventDecoderRegistry eventDecoderRegistry;
  @Autowired private RecordingFeatureProducer recordingFeatureProducer;
  @Autowired private ByodListenerContainerFactory byodListenerContainerFactory;
  @Autowired private ByodDeadLetterConsumerManager deadLetterConsumerManager;
  @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;
  @Autowired private NamedParameterJdbcTemplate jdbc;
  @Autowired private ProcessorDeadLetterRecordRepository repository;
  @Autowired private ByodRuntimeObservationCoalescer runtimeObservationCoalescer;

  private UUID sourceId;
  private String topic;
  private String dltTopic;
  private KafkaMessageListenerContainer<String, byte[]> listenerContainer;

  @BeforeEach
  void beforeEach() throws Exception {
    jdbc.getJdbcTemplate().execute("TRUNCATE TABLE processor.processor_dead_letter_record CASCADE");
    reset(blobClient, eventDecoderRegistry, runtimeObservationCoalescer);
    recordingFeatureProducer.reset();

    sourceId = UUID.randomUUID();
    topic = "opencop.byod.partition-proof." + sourceId;
    dltTopic = topic + DLT_SUFFIX;
    createTopics();

    listenerContainer = byodListenerContainerFactory.create(sourceId, topic);
    listenerContainer.start();
    awaitAssignment(listenerContainer, 4);
    deadLetterConsumerManager.startConsumer(sourceId, topic);
    awaitAssignment(deadLetterConsumerManager.getAllStatuses().get(sourceId).container(), 1);
  }

  @AfterEach
  void afterEach() {
    if (listenerContainer != null) {
      listenerContainer.stop();
    }
    deadLetterConsumerManager.stopConsumer(sourceId);
  }

  @Test
  void routesPartitionThreeFailureToSinglePartitionDltAndPreservesOriginalPartitionHeader()
      throws Exception {
    var payload = "bad-payload".getBytes(StandardCharsets.UTF_8);

    doThrow(new IllegalStateException("boom"))
        .when(eventDecoderRegistry)
        .decodeAndMapRealTimeResult(any(UUID.class), any(MappingProperties.class), eq(payload));

    kafkaTemplate.send(topic, 3, "key-bad", payload);
    kafkaTemplate.flush();

    verify(eventDecoderRegistry, timeout(5000).times(2))
        .decodeAndMapRealTimeResult(
            any(UUID.class), eq(new MappingProperties(sourceId, null, false)), eq(payload));
    assertNull(recordingFeatureProducer.pollPublished(Duration.ofMillis(500)));

    var failure = awaitFailureByOriginalTopic(topic);
    assertEquals(DeadLetterRecord.Stage.BYOD, failure.stage());
    assertEquals(topic, failure.originalTopic());
    assertEquals(3, failure.originalPartition());
    assertEquals(dltTopic, failure.dltTopic());
    assertEquals(0, failure.dltPartition());
    assertEquals(
        "kafka://%s/%d/%d".formatted(dltTopic, 0, failure.dltOffset()), failure.payloadRef());
    assertNull(failure.payloadSizeBytes());
    verify(runtimeObservationCoalescer, never()).recordSuccess(any(), any(), any());
  }

  private void createTopics() throws InterruptedException, ExecutionException {
    try (var admin =
        AdminClient.create(
            Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                embeddedKafkaBroker.getBrokersAsString()))) {
      admin
          .createTopics(
              List.of(new NewTopic(topic, 4, (short) 1), new NewTopic(dltTopic, 1, (short) 1)))
          .all()
          .get();
    }
  }

  private static void awaitAssignment(
      KafkaMessageListenerContainer<String, byte[]> container, int partitions) {
    assertTimeoutPreemptively(
        Duration.ofSeconds(15),
        () -> ContainerTestUtils.waitForAssignment(container, partitions),
        "Timed out waiting for BYOD listener container assignment");
  }

  private DeadLetterRecord awaitFailureByOriginalTopic(String originalTopic)
      throws InterruptedException {
    long deadline = System.nanoTime() + ASYNC_TIMEOUT.toNanos();
    DeadLetterRecord failure = failureByOriginalTopic(originalTopic);
    while (failure == null && System.nanoTime() < deadline) {
      Thread.sleep(100L);
      failure = failureByOriginalTopic(originalTopic);
    }
    if (failure == null) {
      throw new AssertionError("Timed out waiting for indexed BYOD DLT failure");
    }
    return failure;
  }

  private DeadLetterRecord failureByOriginalTopic(String originalTopic) {
    return jdbc
        .query(
            """
            SELECT id
              FROM processor.processor_dead_letter_record
             WHERE original_topic = :originalTopic
            """,
            Map.of("originalTopic", originalTopic),
            (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class))
        .stream()
        .findFirst()
        .flatMap(repository::findById)
        .orElse(null);
  }
}
