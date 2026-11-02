// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.business.usecase.event;

import com.opencop.api.connect.ports.usecase.event.EventHandler;
import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * EventHandler that sends raw bytes to Kafka with metadata in headers.
 *
 * <p>This is the RECOMMENDED approach for generic TCP connections: - Zero serialization overhead
 * (no base64 encoding) - Faster: no JSON encode/decode (~50% faster) - Smaller messages: ~80%
 * reduction vs JSON wrapper - Kafka-native pattern (headers for metadata)
 *
 * <p>Headers added: - opencop-source-id: Source server UUID (aligns with processor expectations) -
 * opencop-event-id: Unique event identifier - opencop-type: Automatically added by
 * MessageBrokerProducer
 *
 * <p>The Processor API will loop through decoders to detect the payload format (for example
 * GeoJSON) for each message.
 */
public class RawBytesEventHandler implements EventHandler<byte[]> {

  private final MessageBrokerProducer<byte[]> producer;

  public RawBytesEventHandler(MessageBrokerProducer<byte[]> producer) {
    this.producer = producer;
  }

  @Override
  public void handle(UUID serverId, byte[] rawBytes) {
    Map<String, String> headers = new HashMap<>();
    headers.put(OpencopKafkaHeaders.SOURCE_ID.getName(), serverId.toString());
    headers.put(OpencopKafkaHeaders.OBJECT_ID.getName(), UUID.randomUUID().toString());
    // The partitioning strategy is still unsettled. Messages currently have no key, which gives
    // round-robin distribution and no ordering guarantee. A sourceId key would preserve ordering
    // per server and keep a hot server within its assigned partition.
    this.producer.send(rawBytes, headers);
  }
}
