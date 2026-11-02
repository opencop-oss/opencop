// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config.byod;

import static com.opencop.core.sdk.technical.gateway.broker.kafka.KafkaNaming.DLT_SUFFIX;

import com.opencop.core.sdk.helper.InstanceIdentity;
import com.opencop.core.sdk.technical.gateway.broker.kafka.config.BaseKafkaConfig;
import com.opencop.processor.technical.spring.consumer.ByodMessageHandler;
import com.opencop.processor.technical.spring.dlt.DeadLetterHeaders;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer.HeaderNames.HeadersToAdd;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Creates a {@link KafkaMessageListenerContainer} for a single BYOD topic. Each container gets its
 * own consumer group ({@code processor-byod-<sourceId>}), explicit ack mode, and DLT error handling
 * via the shared {@code byodDltKafkaTemplate}.
 */
@Component
public class ByodListenerContainerFactory {

  private final ByodConsumerProperties properties;
  private final ByodMessageHandler messageHandler;
  private final KafkaTemplate<String, byte[]> dltTemplate;
  private final String instanceId;
  private final BaseKafkaConfig baseKafkaConfig;

  ByodListenerContainerFactory(
      BaseKafkaConfig baseKafkaConfig,
      ByodConsumerProperties properties,
      ByodMessageHandler messageHandler,
      @Qualifier("byodDltKafkaTemplate") KafkaTemplate<String, byte[]> dltTemplate) {
    this.baseKafkaConfig = baseKafkaConfig;
    this.properties = properties;
    this.messageHandler = messageHandler;
    this.dltTemplate = dltTemplate;
    this.instanceId = InstanceIdentity.resolveInstanceId("processor");
  }

  public KafkaMessageListenerContainer<String, byte[]> create(UUID sourceId, String topicName) {
    var props =
        new HashMap<String, Object>(
            Map.ofEntries(
                Map.entry(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class),
                Map.entry(
                    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class),
                Map.entry(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false),
                Map.entry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, properties.autoOffsetReset()),
                Map.entry(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, properties.maxPollRecords()),
                Map.entry(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, properties.sessionTimeoutMs()),
                Map.entry(
                    ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, properties.heartbeatIntervalMs()),
                Map.entry(
                    ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, properties.maxPollIntervalMs()),
                Map.entry(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, properties.fetchMinBytes()),
                Map.entry(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, properties.fetchMaxBytes()),
                Map.entry(
                    ConsumerConfig.CLIENT_ID_CONFIG,
                    "processor-byod-" + sourceId + "-" + instanceId)));
    props.putAll(baseKafkaConfig.props());

    var consumerFactory = new DefaultKafkaConsumerFactory<String, byte[]>(props);

    var containerProps = new ContainerProperties(topicName);
    containerProps.setAckMode(ContainerProperties.AckMode.RECORD);
    containerProps.setGroupId("processor-byod-" + sourceId);
    containerProps.setMessageListener(messageHandler);

    var container = new KafkaMessageListenerContainer<>(consumerFactory, containerProps);

    var recoverer = recoverer();
    var errorHandler =
        new DefaultErrorHandler(
            recoverer, new FixedBackOff(properties.retryBackoffMs(), properties.retryAttempts()));
    container.setCommonErrorHandler(errorHandler);

    return container;
  }

  DeadLetterPublishingRecoverer recoverer() {
    // re. -1 `partition` see:
    // https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html#publishing-dead-letter-records
    // > If the returned TopicPartition has a negative partition, the partition is not set in the
    // ProducerRecord, so the partition is selected by Kafka.
    // DLTs may only have fewer partitions than the base topic or may have only a single partition
    var recoverer =
        new DeadLetterPublishingRecoverer(
            dltTemplate, (rec, _) -> new TopicPartition(rec.topic() + DLT_SUFFIX, -1));
    recoverer.excludeHeader(HeadersToAdd.EX_STACKTRACE);
    recoverer.setHeadersFunction(DeadLetterHeaders::forByodRecoverer);
    return recoverer;
  }
}
