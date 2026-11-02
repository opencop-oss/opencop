// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.dataimport;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configurable properties for layer data imports. */
@ConfigurationProperties(prefix = "opencop.layer.import")
public class LayerImportProperties {

  /** Maximum allowed import file size in bytes. Default: 50 MB. */
  private long maxBytes = 50L * 1_024 * 1_024;

  public long getMaxBytes() {
    return maxBytes;
  }

  public void setMaxBytes(long maxBytes) {
    this.maxBytes = maxBytes;
  }
}
