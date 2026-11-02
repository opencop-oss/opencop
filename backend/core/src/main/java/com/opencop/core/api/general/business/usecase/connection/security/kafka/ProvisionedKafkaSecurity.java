// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.security.kafka;

import com.opencop.core.sdk.ports.gateway.kafka.KafkaCredentials;
import java.util.Optional;

/**
 * What a Kafka connection's security material became once provisioned on the broker.
 *
 * @param credentials the topic and producer credentials the connection publishes with
 * @param cleanup releases what provisioning created on the broker, should the connection be
 *     rejected before it is stored; empty when it created nothing
 */
public record ProvisionedKafkaSecurity(KafkaCredentials credentials, Optional<Runnable> cleanup) {}
