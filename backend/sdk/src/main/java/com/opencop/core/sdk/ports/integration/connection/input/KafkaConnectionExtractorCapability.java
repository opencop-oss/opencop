// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.connection.input;

import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.material.kafka.KafkaConnectionMaterial;

/**
 * A Kafka extractor: it reads a connection's input and produces Kafka connection material.
 *
 * <p>The registry requires it of every KAFKA extractor, so a lookup typed by {@link
 * ExtractorProtocol#KAFKA} returns an extractor that produces this material.
 */
public interface KafkaConnectionExtractorCapability
    extends ConnectionExtractorCapability<ConnectionInput, KafkaConnectionMaterial> {

  @Override
  default TransportProtocol protocol() {
    return TransportProtocol.KAFKA;
  }
}
