// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.consumer.byod;

import static com.opencop.core.sdk.technical.gateway.broker.kafka.KafkaNaming.DLT_SUFFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.kafka.support.KafkaHeaders.DLT_EXCEPTION_FQCN;
import static org.springframework.kafka.support.KafkaHeaders.DLT_EXCEPTION_MESSAGE;
import static org.springframework.kafka.support.KafkaHeaders.DLT_ORIGINAL_OFFSET;
import static org.springframework.kafka.support.KafkaHeaders.DLT_ORIGINAL_PARTITION;

import com.opencop.core.sdk.ports.gateway.blob.BlobClient;
import com.opencop.core.sdk.technical.gateway.mapping.ingress.EventDecoderRegistry;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import com.opencop.processor.component.consumer.realtime.RecordingFeatureProducer;
import com.opencop.processor.component.support.AbstractProcessorKafkaDatabaseIT;
import com.opencop.processor.component.support.KafkaTestRecords;
import com.opencop.processor.component.support.ProcessorKafkaComponentTestApplication;
import com.opencop.processor.technical.spring.config.byod.ByodConsumerManager;
import com.opencop.processor.technical.spring.config.byod.ByodDeadLetterConsumerManager;
import com.opencop.processor.technical.spring.consumer.ByodRuntimeObservationCoalescer;
import com.opencop.processor.technical.spring.dlt.DeadLetterFailureIndexer;
import com.opencop.processor.technical.spring.dlt.DeadLetterHeaders;
import com.opencop.processor.technical.spring.dlt.DeadLetterRecord;
import com.opencop.processor.technical.spring.dlt.ProcessorDeadLetterRecordRepository;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
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
class ByodDeadLetterIndexingComponentIT extends AbstractProcessorKafkaDatabaseIT {

  private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(25);
  private static final int MAX_JSON_BYTES = 16 * 1024;
  private static final int MAX_EXCEPTION_MESSAGE_CHARS = 4 * 1024;

  @Autowired private BlobClient blobClient;
  @Autowired private ByodConsumerManager byodConsumerManager;
  @Autowired private ByodDeadLetterConsumerManager deadLetterConsumerManager;
  @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;
  @Autowired private EventDecoderRegistry eventDecoderRegistry;
  @Autowired private KafkaTemplate<String, byte[]> kafkaTemplate;
  @Autowired private NamedParameterJdbcTemplate jdbc;
  @Autowired private ProcessorDeadLetterRecordRepository repository;
  @Autowired private RecordingFeatureProducer recordingFeatureProducer;
  @Autowired private ByodRuntimeObservationCoalescer runtimeObservationCoalescer;

  private UUID sourceId;
  private String topic;
  private String dltTopic;

  @BeforeEach
  void beforeEach() throws Exception {
    jdbc.getJdbcTemplate().execute("TRUNCATE TABLE processor.processor_dead_letter_record CASCADE");
    reset(blobClient, eventDecoderRegistry, runtimeObservationCoalescer);
    recordingFeatureProducer.reset();

    sourceId = UUID.randomUUID();
    topic = "opencop.byod.index." + sourceId;
    dltTopic = topic + DLT_SUFFIX;
    createTopics(1, 1);
  }

  @AfterEach
  void afterEach() {
    byodConsumerManager.stopConsumer(sourceId);
  }

  @Test
  void decoderFailureIsIndexedWithSourceContextAfterRetryPublishesDltMessage() throws Exception {
    var payload = "bad-payload".getBytes(StandardCharsets.UTF_8);
    startSourceConsumer(1, 1);

    doThrow(new IllegalStateException("boom"))
        .when(eventDecoderRegistry)
        .decodeAndMapRealTimeResult(any(UUID.class), any(MappingProperties.class), eq(payload));

    kafkaTemplate.send(topic, "key-bad", payload);
    kafkaTemplate.flush();

    verify(eventDecoderRegistry, timeout(5000).times(2))
        .decodeAndMapRealTimeResult(
            any(UUID.class), eq(new MappingProperties(sourceId, null, false)), eq(payload));

    var failure = awaitFailureByOriginalTopic(topic);
    assertThat(failure.stage()).isEqualTo(DeadLetterRecord.Stage.BYOD);
    assertThat(failure.failureKind()).isEqualTo(DeadLetterRecord.FailureKind.PROCESSING);
    assertThat(failure.originalTopic()).isEqualTo(topic);
    assertThat(failure.dltTopic()).isEqualTo(dltTopic);
    assertThat(failure.contextJson()).isEqualTo("{\"sourceId\":\"" + sourceId + "\"}");
    assertThat(failure.payloadRef())
        .isEqualTo(
            "kafka://%s/%d/%d"
                .formatted(failure.dltTopic(), failure.dltPartition(), failure.dltOffset()));
    assertThat(failure.payloadExpiresAt()).isNotNull();
    assertThat(failure.payloadSha256()).isNull();
    assertThat(failure.payloadSizeBytes()).isNull();
    verify(blobClient, never()).upload(any(String.class), any(InputStream.class));
  }

  @Test
  void stoppingSourceConsumerStopsMatchingDltIndexConsumer() throws Exception {
    startSourceConsumer(1, 1);
    assertThat(byodConsumerManager.isRunning(sourceId)).isTrue();
    assertThat(deadLetterConsumerManager.isRunning(sourceId)).isTrue();

    byodConsumerManager.stopConsumer(sourceId);

    assertThat(byodConsumerManager.isRunning(sourceId)).isFalse();
    assertThat(deadLetterConsumerManager.isRunning(sourceId)).isFalse();
  }

  @Test
  void malformedAndOversizedDltHeadersAreBoundedByTheDynamicListenerPath() throws Exception {
    var payload = "hostile-payload".getBytes(StandardCharsets.UTF_8);
    startSourceConsumer(1, 1);

    var sendResult =
        kafkaTemplate.send(adversarialDltMessage("adversarial-byod-dlt", payload)).get();

    var failure =
        awaitFailureByDltCoordinates(dltTopic, 0, sendResult.getRecordMetadata().offset());
    assertThat(failure.stage()).isEqualTo(DeadLetterRecord.Stage.UNKNOWN);
    assertThat(failure.failureKind()).isEqualTo(DeadLetterRecord.FailureKind.UNKNOWN);
    assertThat(failure.originalTopic()).isEqualTo("UNKNOWN");
    assertThat(failure.originalPartition()).isNull();
    assertThat(failure.originalOffset()).isNull();
    assertThat(failure.exceptionClass()).isEqualTo("UNKNOWN");
    assertThat(failure.exceptionMessage()).hasSize(MAX_EXCEPTION_MESSAGE_CHARS);
    assertThat(failure.contextJson().getBytes(StandardCharsets.UTF_8))
        .hasSizeLessThanOrEqualTo(MAX_JSON_BYTES);
    assertThat(failure.headersJson().getBytes(StandardCharsets.UTF_8))
        .hasSizeLessThanOrEqualTo(MAX_JSON_BYTES);
    assertThat(failure.payloadRef())
        .isEqualTo(
            "kafka://%s/%d/%d"
                .formatted(failure.dltTopic(), failure.dltPartition(), failure.dltOffset()));
    assertThat(failure.payloadExpiresAt()).isNotNull();
    assertThat(failure.payloadSizeBytes()).isNull();
  }

  private void createTopics(int sourcePartitions, int dltPartitions)
      throws InterruptedException, ExecutionException {
    try (var admin =
        AdminClient.create(
            Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                embeddedKafkaBroker.getBrokersAsString()))) {
      admin
          .createTopics(
              List.of(
                  new NewTopic(topic, sourcePartitions, (short) 1),
                  new NewTopic(dltTopic, dltPartitions, (short) 1)))
          .all()
          .get();
    }
  }

  private void startSourceConsumer(int sourcePartitions, int dltPartitions) {
    byodConsumerManager.startConsumer(sourceId, topic);
    ContainerTestUtils.waitForAssignment(
        byodConsumerManager.getAllStatuses().get(sourceId).container(), sourcePartitions);
    ContainerTestUtils.waitForAssignment(
        deadLetterConsumerManager.getAllStatuses().get(sourceId).container(), dltPartitions);
  }

  private DeadLetterRecord awaitFailureByOriginalTopic(String originalTopic)
      throws InterruptedException {
    return awaitFailure(
        () ->
            jdbc
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
                .orElse(null));
  }

  private DeadLetterRecord awaitFailureByDltCoordinates(
      String failureDltTopic, int dltPartition, long dltOffset) throws InterruptedException {
    return awaitFailure(
        () ->
            repository.findByDltCoordinates(failureDltTopic, dltPartition, dltOffset).orElse(null));
  }

  private DeadLetterRecord awaitFailure(FailureSupplier supplier) throws InterruptedException {
    long deadline = System.nanoTime() + ASYNC_TIMEOUT.toNanos();
    DeadLetterRecord failure = supplier.get();
    while (failure == null && System.nanoTime() < deadline) {
      Thread.sleep(100L);
      failure = supplier.get();
    }
    assertThat(failure).isNotNull();
    return failure;
  }

  private ProducerRecord<String, byte[]> adversarialDltMessage(String key, byte[] payload) {
    return KafkaTestRecords.byteArrayRecord(
        dltTopic,
        key,
        payload,
        Map.of(
            DeadLetterHeaders.STAGE,
            "NOT_A_STAGE",
            DeadLetterHeaders.FAILURE_KIND,
            "NOT_A_KIND",
            DLT_ORIGINAL_PARTITION,
            "not-an-int",
            DLT_ORIGINAL_OFFSET,
            "not-a-long",
            DLT_EXCEPTION_FQCN,
            " ",
            DLT_EXCEPTION_MESSAGE,
            "x".repeat(MAX_EXCEPTION_MESSAGE_CHARS + 128),
            DeadLetterFailureIndexer.CONTEXT_HEADER,
            "c".repeat(MAX_JSON_BYTES + 128),
            "oversized-header",
            "h".repeat(MAX_JSON_BYTES + 128)));
  }

  @FunctionalInterface
  private interface FailureSupplier {
    DeadLetterRecord get() throws InterruptedException;
  }
}
