// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.material.kafka;

/**
 * A producer authenticated by broker-provisioned SCRAM credentials.
 *
 * @param producerByteRate the producer throttle of the SCRAM user in bytes per second, or null for
 *     the broker default
 */
public record ScramSecurityMaterial(Long producerByteRate) implements KafkaSecurityMaterial {}
