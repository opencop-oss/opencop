// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.consumer;

import static com.opencop.core.sdk.technical.gateway.broker.kafka.KafkaNaming.DLT_SUFFIX;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.opencop.core.sdk.ports.gateway.repository.RepositoryException;
import com.opencop.core.sdk.technical.gateway.broker.kafka.ProvenanceHeaders;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import com.opencop.processor.business.usecase.event.EventAutoAttachmentService;
import com.opencop.processor.ports.business.usecase.event.PersistenceFailureException;
import com.opencop.processor.technical.gateway.db.PostGisFeatureRecordMapper;
import com.opencop.processor.technical.gateway.model.PostGisFeatureRecord;
import com.opencop.processor.technical.spring.dlt.DeadLetterContext;
import com.opencop.processor.technical.spring.dlt.DeadLetterHeaders;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.geojson.Feature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

@Component
public class PersistenceConsumer {

  private static final Logger log = LoggerFactory.getLogger(PersistenceConsumer.class);

  private final PostGisFeatureRecordMapper postGisFeatureRecordMapper;
  private final RetryingPersistenceService retryingPersistenceService;
  private final KafkaTemplate<String, Feature> featureDltTemplate;
  private final EventAutoAttachmentService autoAttachmentService;

  PersistenceConsumer(
      final PostGisFeatureRecordMapper postGisFeatureRecordMapper,
      final RetryingPersistenceService retryingPersistenceService,
      @Qualifier("featureDltTemplate") final KafkaTemplate<String, Feature> featureDltTemplate,
      final EventAutoAttachmentService autoAttachmentService) {
    this.postGisFeatureRecordMapper = postGisFeatureRecordMapper;
    this.retryingPersistenceService = retryingPersistenceService;
    this.featureDltTemplate = featureDltTemplate;
    this.autoAttachmentService = autoAttachmentService;
  }

  @KafkaListener(
      id = "persistConsumer",
      topics = "${opencop.messaging-broker.listeners.persist.topic}",
      containerFactory = "persistFactory")
  public void persist(List<ConsumerRecord<String, Feature>> records) {
    if (records.isEmpty()) {
      return;
    }

    long startedAtNanos = System.nanoTime();
    log.debug("Consuming persistence batch: count={}", records.size());

    try {
      var preparedEntries = new ArrayList<PreparedEntry>(records.size());
      var invalidRecords = new ArrayList<InvalidPersistenceRecord>();

      for (var sourceRecord : records) {
        try {
          preparedEntries.add(new PreparedEntry(sourceRecord, prepare(sourceRecord)));
        } catch (RuntimeException e) {
          invalidRecords.add(new InvalidPersistenceRecord(sourceRecord, e));
        }
      }

      if (!preparedEntries.isEmpty()) {
        persistPrepared(
            preparedEntries.stream().map(PreparedEntry::prepared).toList(), batchKey(records));
        try {
          autoAttachFromSource(preparedEntries);
        } catch (Exception e) {
          log.warn("Auto-attachment step failed, continuing: {}", e.getMessage());
        }
      }

      // Persist prepared rows before publishing manual invalid-record DLTs. Prepared-save failures
      // intentionally stay on Spring's batch retry/recovery path while deterministic preparation
      // failures are isolated here.
      awaitDltPublishes(publishInvalidRecords(invalidRecords));

      log.info(
          "Persistence batch processed successfully: count={}, preparedCount={}, deadLetteredCount={}, durationMs={}",
          records.size(),
          preparedEntries.size(),
          invalidRecords.size(),
          ConsumerLogSupport.elapsedMillisSince(startedAtNanos));
    } catch (Exception e) {
      log.error(
          "Persistence batch failed: count={}, durationMs={}, featureIds={}",
          records.size(),
          ConsumerLogSupport.elapsedMillisSince(startedAtNanos),
          ConsumerLogSupport.featureIds(records.stream().map(ConsumerRecord::value).toList()),
          e);
      throw e;
    }
  }

  /** Each attachment is best-effort: a failure must not skip remaining records in the batch. */
  private void autoAttachFromSource(List<PreparedEntry> entries) {
    for (var entry : entries) {
      var source = entry.prepared().source();
      var objectId = entry.prepared().objectId();
      runBestEffort(
          source, objectId, () -> autoAttachmentService.attachFromRecord(source, objectId));
    }
  }

  private void runBestEffort(UUID source, String objectId, Runnable attach) {
    try {
      attach.run();
    } catch (RuntimeException e) {
      log.warn(
          "Auto-attachment failed, continuing: sourceId={}, objectId={}: {}",
          source,
          objectId,
          e.toString());
    }
  }

  /**
   * Prepares a consumed record for persistence, reading its provenance off the record's headers.
   *
   * <p>This is the last point at which provenance exists: the feature payload never carried it, so
   * anything not read here is lost. A record without the headers prepares with empty provenance,
   * and the upsert's {@code COALESCE} rules then leave any already-known values intact.
   */
  private PostGisFeatureRecord prepare(ConsumerRecord<String, Feature> sourceRecord) {
    try {
      return postGisFeatureRecordMapper.toRecord(
          sourceRecord.value(), ProvenanceHeaders.from(sourceRecord.headers()));
    } catch (RepositoryException e) {
      throw persistenceException(e);
    }
  }

  private void persistPrepared(List<PostGisFeatureRecord> input, String batchKey) {
    retryingPersistenceService.persist(input, batchKey);
  }

  /**
   * A stable, bounded identity for this batch, used so the resource activity it represents is
   * recorded once.
   *
   * <p>Derived from the highest offset per partition, which is what makes it stable: a redelivery
   * of the same records recomputes the same key, and a deadlock retry inside persistence reuses it.
   * Partitions are sorted so the key does not depend on the order the broker happened to return
   * them.
   *
   * <p>Hashed rather than concatenated because {@code activity_id} is {@code varchar(255)} and a
   * poll spanning a dozen partitions would otherwise exceed it — failing the insert with SQLSTATE
   * class 54, which reads as retryable infrastructure rather than as a key that was too long. The
   * topic stays in the clear so a ledger row is still recognizable.
   *
   * <p>A batch redelivered with different composition — after a rebalance, say — computes a
   * different key and is recorded again. That is harmless rather than incorrect: each fact carries
   * the batch's newest reported time, and the projection only moves forward, so the second
   * recording writes a timestamp no newer than the first.
   */
  private static String batchKey(List<ConsumerRecord<String, Feature>> records) {
    var highestOffsets = new TreeMap<String, Long>();
    for (var record : records) {
      highestOffsets.merge(record.topic() + ":" + record.partition(), record.offset(), Math::max);
    }
    var canonical =
        highestOffsets.entrySet().stream()
            .map(entry -> entry.getKey() + ":" + entry.getValue())
            .collect(Collectors.joining("|"));
    var topic = highestOffsets.firstKey().substring(0, highestOffsets.firstKey().lastIndexOf(':'));
    return topic + ":" + sha256Hex(canonical);
  }

  private static String sha256Hex(String value) {
    try {
      var digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(UTF_8));
      var hex = new StringBuilder(digest.length * 2);
      for (var b : digest) {
        hex.append("%02x".formatted(b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 is required of every JVM, so this cannot happen in practice.
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private List<CompletableFuture<SendResult<String, Feature>>> publishInvalidRecords(
      List<InvalidPersistenceRecord> invalidRecords) {
    var futures =
        new ArrayList<CompletableFuture<SendResult<String, Feature>>>(invalidRecords.size());

    for (var invalidRecord : invalidRecords) {
      log.warn(
          "Dead-lettering invalid persistence record: key={}, partition={}, offset={}, featureId={}",
          invalidRecord.consumerRecord().key(),
          invalidRecord.consumerRecord().partition(),
          invalidRecord.consumerRecord().offset(),
          invalidRecord.consumerRecord().value() != null
              ? invalidRecord.consumerRecord().value().getId()
              : null,
          invalidRecord.failure());
      futures.add(
          featureDltTemplate.send(
              dltRecordFor(invalidRecord.consumerRecord(), invalidRecord.failure())));
    }

    return futures;
  }

  private void awaitDltPublishes(List<CompletableFuture<SendResult<String, Feature>>> futures) {
    for (var future : futures) {
      try {
        future.get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while awaiting persistence DLT publish", e);
      } catch (ExecutionException e) {
        throw new IllegalStateException("Failed to publish invalid persistence record to DLT", e);
      }
    }
  }

  private ProducerRecord<String, Feature> dltRecordFor(
      ConsumerRecord<String, Feature> sourceRecord, RuntimeException failure) {
    // Set `null` for partition so Kafka will choose it; DLT likely has fewer partitions
    // than base topic
    // see:
    // https://kafka.apache.org/40/javadoc/org/apache/kafka/clients/producer/ProducerRecord.html
    // > A key/value pair to be sent to Kafka. This consists of ... an optional partition number,
    // and ...
    return new ProducerRecord<>(
        sourceRecord.topic() + DLT_SUFFIX,
        null,
        null,
        sourceRecord.key(),
        sourceRecord.value(),
        DeadLetterHeaders.forManualPersistence(
            sourceRecord, failure, contextFromFeature(sourceRecord.value())));
  }

  private DeadLetterContext contextFromFeature(Feature feature) {
    try {
      var properties = OpenCopPropertiesParser.readFromFeature(feature);
      return properties == null
          ? null
          : new DeadLetterContext(properties.getSource(), null, null, properties.getImportId());
    } catch (RuntimeException _) {
      return null;
    }
  }

  private PersistenceFailureException persistenceException(RepositoryException e) {
    return new PersistenceFailureException(
        "Could not persist features to DB | code: " + e.getCode(), e);
  }

  private record InvalidPersistenceRecord(
      ConsumerRecord<String, Feature> consumerRecord, RuntimeException failure) {}

  private record PreparedEntry(
      ConsumerRecord<String, Feature> original, PostGisFeatureRecord prepared) {}
}
