// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.material.kafka;

import java.util.Objects;

/**
 * The security material of a Kafka connection. A Kafka connection has no endpoint of its own: its
 * topic is derived from the connection when the broker resources are provisioned.
 */
public record KafkaConnectionMaterial(KafkaSecurityMaterial security) {

  public KafkaConnectionMaterial {
    Objects.requireNonNull(security, "security");
  }
}
