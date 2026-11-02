// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.kafka;

public record KafkaCredentials(
    String topicName, String username, Password password, String bootstrapServer) {}
