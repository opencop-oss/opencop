// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.blob;

import com.opencop.core.sdk.ports.gateway.blob.BlobClient;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BlobConfig {

  @Bean
  public BlobClientFactory cloudStorageClientFactory(
      final Map<String, BlobClient> cloudStorageClientMap) {
    return new BlobClientFactory(cloudStorageClientMap);
  }
}
