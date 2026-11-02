// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.blob;

import com.opencop.core.sdk.ports.gateway.blob.BlobClient;
import java.util.Map;

public class BlobClientFactory {

  private final Map<String, BlobClient> clientMap;

  public BlobClientFactory(Map<String, BlobClient> clientMap) {
    this.clientMap = clientMap;
  }

  public BlobClient getClient(String provider) {
    BlobClient client = clientMap.get(provider.toLowerCase());
    if (client == null) {
      throw new IllegalArgumentException("Unsupported storage provider: " + provider);
    }
    return client;
  }
}
