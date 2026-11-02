// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.dlt;

import com.opencop.processor.technical.spring.config.broker.KafkaConsumersProperties;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.stereotype.Component;

@Component
public class DeadLetterIndexingErrorHandlerFactory {

  private static final Logger log =
      LoggerFactory.getLogger(DeadLetterIndexingErrorHandlerFactory.class);

  private final DeadLetterObservability observability;

  public DeadLetterIndexingErrorHandlerFactory(DeadLetterObservability observability) {
    this.observability = observability;
  }

  public DefaultErrorHandler create(
      String source, KafkaConsumersProperties.DeadLetterIndexingProps properties) {
    var backOff = new ExponentialBackOffWithMaxRetries(properties.getRetryAttempts());
    backOff.setInitialInterval(properties.getRetryInitialIntervalMs());
    backOff.setMultiplier(properties.getRetryMultiplier());
    backOff.setMaxInterval(properties.getRetryMaxIntervalMs());
    return new DefaultErrorHandler(
        (consumerRecord, exception) -> recover(source, consumerRecord, exception), backOff);
  }

  void recover(String source, ConsumerRecord<?, ?> consumerRecord, Exception exception) {
    log.error(
        "DLT indexing retry exhausted; leaving offsets uncommitted: source={}, topic={}, partition={}, offset={}",
        source,
        consumerRecord.topic(),
        consumerRecord.partition(),
        consumerRecord.offset(),
        exception);
    observability.recordIndexingFailure(
        source, DeadLetterObservability.IndexingFailureCause.RETRY_EXHAUSTED);
    throw new IllegalStateException(
        "DLT indexing retry exhausted; leaving offsets uncommitted for "
            + consumerRecord.topic()
            + "-"
            + consumerRecord.partition()
            + "@"
            + consumerRecord.offset(),
        exception);
  }
}
