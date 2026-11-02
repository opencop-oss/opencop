// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.material.kafka;

/**
 * The security material a Kafka connection is created with. Each kind maps to one way the broker
 * authenticates the connection's producer; a new kind is added here and handled wherever these are
 * switched on.
 */
public sealed interface KafkaSecurityMaterial permits ScramSecurityMaterial {}
