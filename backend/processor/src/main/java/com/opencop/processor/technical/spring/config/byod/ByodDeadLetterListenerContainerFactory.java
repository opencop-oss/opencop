// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config.byod;

import static com.opencop.core.sdk.technical.gateway.broker.kafka.KafkaNaming.DLT_SUFFIX;

import com.opencop.core.sdk.helper.InstanceIdentity;
import com.opencop.core.sdk.technical.gateway.broker.kafka.config.BaseKafkaConfig;
import com.opencop.processor.technical.spring.config.broker.KafkaConsumersProperties;
import com.opencop.processor.technical.spring.dlt.DeadLetterFailureIndexer;
import com.opencop.processor.technical.spring.dlt.DeadLetterIndexingErrorHandlerFactory;
import com.opencop.processor.technical.spring.dlt.DeadLetterObservability;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.BatchAcknowledgingMessageListener;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.stereotype.Component;

@Component
public class ByodDeadLetterListenerContainerFactory {

  private static final String GROUP_ID_PREFIX = "processor-byod-";
  private static final String CLIENT_ID_INFIX = "-dlt-indexer-";
  private static final String CONTEXT_JSON_PATTERN = "{\"sourceId\":\"%s\"}";

  private final BaseKafkaConfig baseKafkaConfig;
  private final DeadLetterFailureIndexer indexer;
  private final DeadLetterIndexingErrorHandlerFactory errorHandlerFactory;
  private final KafkaConsumersProperties kafkaConsumersProperties;
  private final String instanceId;

  ByodDeadLetterListenerContainerFactory(
      BaseKafkaConfig baseKafkaConfig,
      DeadLetterFailureIndexer indexer,
      DeadLetterIndexingErrorHandlerFactory errorHandlerFactory,
      KafkaConsumersProperties kafkaConsumersProperties) {
    this.baseKafkaConfig = baseKafkaConfig;
    this.indexer = indexer;
    this.errorHandlerFactory = errorHandlerFactory;
    this.kafkaConsumersProperties = kafkaConsumersProperties;
    this.instanceId = InstanceIdentity.resolveInstanceId("processor");
  }

  public KafkaMessageListenerContainer<String, byte[]> create(UUID sourceId, String topicName) {
    var dltProps = kafkaConsumersProperties.getDltIndexing();
    var cfg =
        new HashMap<String, Object>(
            Map.ofEntries(
                Map.entry(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class),
                Map.entry(
                    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class),
                Map.entry(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false),
                Map.entry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, dltProps.getAutoOffsetReset()),
                Map.entry(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, dltProps.getMaxPollRecords()),
                Map.entry(ConsumerConfig.CLIENT_ID_CONFIG, clientId(sourceId))));
    cfg.putAll(baseKafkaConfig.props());

    var consumerFactory = new DefaultKafkaConsumerFactory<String, byte[]>(cfg);
    var containerProps = new ContainerProperties(topicName + DLT_SUFFIX);
    containerProps.setGroupId(groupId(sourceId));
    containerProps.setAckMode(ContainerProperties.AckMode.MANUAL);
    containerProps.setMessageListener(indexingListener(sourceId));
    var container = new KafkaMessageListenerContainer<>(consumerFactory, containerProps);
    container.setCommonErrorHandler(
        errorHandlerFactory.create(DeadLetterObservability.BYOD, dltProps));
    return container;
  }

  private BatchAcknowledgingMessageListener<String, byte[]> indexingListener(UUID sourceId) {
    return (dltMessages, acknowledgment) -> {
      for (var dltMessage : dltMessages) {
        dltMessage
            .headers()
            .add(
                new RecordHeader(
                    DeadLetterFailureIndexer.CONTEXT_HEADER,
                    CONTEXT_JSON_PATTERN.formatted(sourceId).getBytes(StandardCharsets.UTF_8)));
      }
      indexer.indexBatch(dltMessages);
      acknowledgment.acknowledge();
    };
  }

  private String groupId(UUID sourceId) {
    return GROUP_ID_PREFIX
        + sourceId
        + kafkaConsumersProperties.getDltIndexing().getGroupIdSuffix();
  }

  private String clientId(UUID sourceId) {
    return GROUP_ID_PREFIX + sourceId + CLIENT_ID_INFIX + instanceId;
  }
}
