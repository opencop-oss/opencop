// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.secrets.vault.kv;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "opencop.secrets.vault")
public record VaultSecretsProperties(
    String addr, // e.g. http://vault:8200
    String mount, // e.g. kv
    String tokenFile // e.g. /vault/token/.vault-token
    ) {}
