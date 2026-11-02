// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.broker.kafka.properties;

import com.opencop.core.sdk.technical.gateway.broker.kafka.MessageBrokerProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "opencop.messaging-broker.properties")
public record LocalProviderProperties(
    @NotBlank String bootstrapUrl,
    @NotBlank String saslBootstrapUrl,
    @Positive @DefaultValue("1048576") long defaultProducerByteRate,
    @Positive @DefaultValue("10") int topicPartitions)
    implements ProviderConfig {

  public String name() {
    return MessageBrokerProvider.LOCAL.name();
  }
}
