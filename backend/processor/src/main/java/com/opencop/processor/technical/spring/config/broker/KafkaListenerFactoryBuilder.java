// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config.broker;

import static com.opencop.core.sdk.technical.gateway.broker.kafka.KafkaNaming.DLT_SUFFIX;

import com.opencop.core.sdk.technical.model.serializer.kafka.KafkaGeoJsonDeserializer;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import com.opencop.processor.technical.spring.dlt.DeadLetterHeaders;
import io.micrometer.core.instrument.Tags;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.geojson.Feature;
import org.geojson.GeoJsonObject;
import org.jspecify.annotations.Nullable;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer.HeaderNames.HeadersToAdd;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.util.backoff.FixedBackOff;

public class KafkaListenerFactoryBuilder {

  /**
   * One count per record successfully published to a dead-letter topic, tagged by pipeline stage.
   * The DLTs are shared across listeners and DLT indexing is off by default, so this counter is the
   * only always-on signal that attributes permanent loss to a specific stage.
   */
  static final String DLT_PUBLISHED_METRIC = "opencop_processor_dlt_published_total";

  /**
   * Observer default for contexts with nothing to observe (generic component-test factories that
   * never build a retry-in-place listener) — a null object rather than a nullable field, the same
   * pattern as {@link ProcessorMetrics#noop()}. Production wiring passes the watchbox tracker; this
   * builder deliberately knows only the {@link RetryListener} contract, not the tracker type.
   */
  static final RetryListener NOOP_RETRY_LISTENER = (record, exception, deliveryAttempt) -> {};

  private final ProcessorMetrics metrics;
  private final RetryListener retainedRetryListener;

  public KafkaListenerFactoryBuilder() {
    this(ProcessorMetrics.noop());
  }

  public KafkaListenerFactoryBuilder(ProcessorMetrics metrics) {
    this(metrics, NOOP_RETRY_LISTENER);
  }

  public KafkaListenerFactoryBuilder(
      ProcessorMetrics metrics, RetryListener retainedRetryListener) {
    this.metrics = metrics;
    this.retainedRetryListener =
        Objects.requireNonNull(retainedRetryListener, "retainedRetryListener");
  }

  /**
   * Cadence for retained-batch retries. Fixed rather than exponential: the retry is an in-memory
   * reinvocation (no broker traffic), and a steady probe finds a cleared transient condition within
   * one interval.
   */
  static final long RETRY_IN_PLACE_INTERVAL_MILLIS = 5_000;

  public ConcurrentKafkaListenerContainerFactory<String, byte[]> byteArrayFactory(
      Map<String, Object> baseProps,
      KafkaConsumersProperties.ListenerProps listenerProps,
      long pollTimeoutMs,
      KafkaTemplate<String, byte[]> dltTemplate) {
    return byteArrayFactory(
        baseProps, listenerProps, pollTimeoutMs, recovererFor(dltTemplate, stage(listenerProps)));
  }

  public ConcurrentKafkaListenerContainerFactory<String, byte[]> byteArrayFactory(
      Map<String, Object> baseProps,
      KafkaConsumersProperties.ListenerProps listenerProps,
      long pollTimeoutMs,
      ConsumerRecordRecoverer recoverer) {
    var valueDeserializer = new ErrorHandlingDeserializer<>(new ByteArrayDeserializer());
    ConsumerFactory<String, byte[]> consumerFactory =
        new DefaultKafkaConsumerFactory<>(
            consumerConfig(baseProps, listenerProps), new StringDeserializer(), valueDeserializer);

    var factory = new ConcurrentKafkaListenerContainerFactory<String, byte[]>();
    factory.setConsumerFactory(consumerFactory);
    configureCommon(factory, listenerProps, pollTimeoutMs, recoverer);
    return factory;
  }

  public ConcurrentKafkaListenerContainerFactory<String, Feature> featureFactory(
      Map<String, Object> baseProps,
      KafkaConsumersProperties.ListenerProps listenerProps,
      long pollTimeoutMs,
      KafkaTemplate<String, Feature> dltTemplate) {
    ConsumerFactory<String, GeoJsonObject> consumerFactory =
        new DefaultKafkaConsumerFactory<>(
            consumerConfig(baseProps, listenerProps),
            new StringDeserializer(),
            new ErrorHandlingDeserializer<>(new KafkaGeoJsonDeserializer()));

    var factory = new ConcurrentKafkaListenerContainerFactory<String, Feature>();
    factory.setConsumerFactory(consumerFactory);
    configureCommon(
        factory, listenerProps, pollTimeoutMs, recovererFor(dltTemplate, stage(listenerProps)));
    return factory;
  }

  private Map<String, Object> consumerConfig(
      Map<String, Object> baseProps, KafkaConsumersProperties.ListenerProps listenerProps) {
    var cfg = new HashMap<String, Object>();
    cfg.put(ConsumerConfig.GROUP_ID_CONFIG, listenerProps.getGroupId());
    cfg.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    cfg.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    cfg.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, listenerProps.getMaxPollRecords());
    cfg.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, listenerProps.getMaxWaitMs());
    cfg.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, listenerProps.getMinSizeBytes());
    cfg.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, listenerProps.getMaxSizeBytes() * 2);
    cfg.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, listenerProps.getMaxSizeBytes());
    cfg.putAll(baseProps);
    // After putAll: an explicit per-listener offset reset must win over any provider base
    // property, or a future base-props addition would silently flip a listener like watchbox
    // back to earliest and replay the topic's full retention backlog.
    if (listenerProps.getAutoOffsetReset() != null) {
      cfg.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, listenerProps.getAutoOffsetReset());
    }
    return cfg;
  }

  private void configureCommon(
      ConcurrentKafkaListenerContainerFactory<String, ?> factory,
      KafkaConsumersProperties.ListenerProps listenerProps,
      long pollTimeoutMs,
      ConsumerRecordRecoverer recoverer) {
    factory.setConcurrency(listenerProps.getConcurrency());
    boolean batch = Boolean.TRUE.equals(listenerProps.getBatch());
    factory.setBatchListener(batch);
    factory
        .getContainerProperties()
        .setAckMode(batch ? ContainerProperties.AckMode.BATCH : ContainerProperties.AckMode.RECORD);
    factory.getContainerProperties().setPollTimeout(pollTimeoutMs);
    if (listenerProps.getIdleEventInterval() != null) {
      factory
          .getContainerProperties()
          .setIdleEventInterval(listenerProps.getIdleEventInterval().toMillis());
      // Idle events are liveness evidence, so their cadence must be the configured interval from
      // the first poll on. The default multiplier stretches the interval 5x until the first record
      // ever arrives, which on a quiet topic was measured to leave a 50s evidence cadence against
      // the detection heartbeat's 60s TTL - one delayed flush from a false amber.
      factory.getContainerProperties().setIdleBeforeDataMultiplier(1.0);
    }
    var handler = errorHandler(recoverer, listenerProps.getMaxRetries());
    if (Boolean.TRUE.equals(listenerProps.getRetryInPlaceFailures())) {
      // Transient retained-batch failures retry in place until their condition clears; the fallback
      // batch handler pauses and polls the consumer between attempts, so long outages neither
      // rebalance the group nor re-fetch from the broker. Everything else keeps the
      // maxRetries-then-DLT contract, and if the failure CHANGES class mid-retry, the reset makes
      // this policy run again instead of wedging the listener on a newly exposed poison failure.
      handler.setBackOffFunction(
          (_, failure) ->
              RetryInPlaceFailureClassifier.retryInPlace(failure)
                  ? new FixedBackOff(RETRY_IN_PLACE_INTERVAL_MILLIS, Long.MAX_VALUE)
                  : null);
      handler.setResetStateOnExceptionChange(true);
      // FailedBatchProcessor.setRetryListeners forwards to the fallback batch handler, whose
      // retry loop notifies failedDelivery on every retained attempt. The Watchbox factory also
      // installs its tracker as a BatchInterceptor, so its eventual successful retry resolves
      // the exact retained episode from positive batch-success evidence.
      handler.setRetryListeners(retainedRetryListener);
    }
    factory.setCommonErrorHandler(handler);
  }

  private DefaultErrorHandler errorHandler(ConsumerRecordRecoverer recoverer, int maxRetries) {
    var backoff = new ExponentialBackOffWithMaxRetries(maxRetries);
    backoff.setInitialInterval(500);
    backoff.setMultiplier(2.0);
    backoff.setMaxInterval(10_000);
    return new DefaultErrorHandler(recoverer, backoff);
  }

  DeadLetterPublishingRecoverer recovererFor(KafkaTemplate<String, ?> dltTemplate) {
    return recovererFor(dltTemplate, null);
  }

  DeadLetterPublishingRecoverer recovererFor(
      KafkaTemplate<String, ?> dltTemplate, DeadLetterHeaders.Stage stage) {
    // re. -1 `partition` see:
    // https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html#publishing-dead-letter-records
    // > If the returned TopicPartition has a negative partition, the partition is not set in the
    // ProducerRecord, so the partition is selected by Kafka.
    // DLTs may only have fewer partitions than the base topic or may have only a single partition
    var recoverer =
        new DeadLetterPublishingRecoverer(
            dltTemplate,
            (consumerRecord, _) -> new TopicPartition(consumerRecord.topic() + DLT_SUFFIX, -1)) {
          @Override
          // Nullability mirrors DeadLetterPublishingRecoverer.accept exactly: only the consumer
          // is nullable there, so annotating the exception would make passing it back to super
          // a contract violation.
          public void accept(
              ConsumerRecord<?, ?> consumerRecord,
              @Nullable Consumer<?, ?> consumer,
              Exception exception) {
            super.accept(consumerRecord, consumer, exception);
            // Counted only after a successful publish: this is the moment the record becomes
            // permanent loss.
            metrics.incrementCounter(
                DLT_PUBLISHED_METRIC,
                Tags.of("stage", (stage == null ? DeadLetterHeaders.Stage.UNKNOWN : stage).name()));
          }
        };
    recoverer.excludeHeader(HeadersToAdd.EX_STACKTRACE);
    recoverer.setHeadersFunction(
        stage == null
            ? DeadLetterHeaders::forSpringRecoverer
            : (consumerRecord, exception) ->
                DeadLetterHeaders.forSpringRecoverer(consumerRecord, exception, stage));
    return recoverer;
  }

  private static DeadLetterHeaders.Stage stage(
      KafkaConsumersProperties.ListenerProps listenerProps) {
    try {
      return listenerProps.getStage() == null
          ? null
          : DeadLetterHeaders.Stage.valueOf(listenerProps.getStage());
    } catch (IllegalArgumentException ex) {
      return DeadLetterHeaders.Stage.UNKNOWN;
    }
  }
}
