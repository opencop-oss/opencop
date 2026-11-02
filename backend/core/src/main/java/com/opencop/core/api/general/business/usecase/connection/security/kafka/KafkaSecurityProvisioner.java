// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.security.kafka;

import com.opencop.core.sdk.business.model.integration.connection.material.kafka.KafkaSecurityMaterial;
import java.util.UUID;

/** Turns one kind of Kafka security material into the broker resources a new connection uses. */
public interface KafkaSecurityProvisioner<M extends KafkaSecurityMaterial> {

  ProvisionedKafkaSecurity provision(UUID sourceId, String sourceName, M material);
}
