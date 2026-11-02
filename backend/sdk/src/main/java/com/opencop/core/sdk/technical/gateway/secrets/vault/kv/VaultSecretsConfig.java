// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.secrets.vault.kv;

import com.opencop.core.sdk.ports.gateway.secrets.SecretStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(VaultSecretsProperties.class)
@ConditionalOnProperty(name = "opencop.secrets.vault.provider", havingValue = "kv")
class VaultSecretsConfig {

  @Bean
  SecretStore secretStore(VaultSecretsProperties props, ObjectMapper objectMapper) {
    return new VaultKvV2CredentialsStore(props, objectMapper);
  }
}
