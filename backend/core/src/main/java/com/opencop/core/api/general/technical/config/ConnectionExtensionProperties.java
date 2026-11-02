// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config;

import com.opencop.core.api.general.business.model.connection.extension.RecordLimits;
import java.time.Duration;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Deployment-wide bounds on what connection extensions may store. */
@ConfigurationProperties(prefix = ConnectionExtensionProperties.PREFIX)
public class ConnectionExtensionProperties implements InitializingBean {

  public static final String PREFIX = "opencop.connection-extensions";

  private static final Duration DEFAULT_DELETE_HOOK_TIMEOUT = Duration.ofSeconds(10);

  private Records records = new Records();

  /** How long one extension's delete hook may run before the host stops waiting for it. */
  private Duration deleteHookTimeout = DEFAULT_DELETE_HOOK_TIMEOUT;

  /** Rejects a non-positive limit at startup, which would otherwise refuse every write. */
  @Override
  public void afterPropertiesSet() {
    requirePositive(records.getMaxDocumentBytes(), "records.max-document-bytes");
    requirePositive(records.getMaxPerExtension(), "records.max-per-extension");
    requirePositive(deleteHookTimeout.toMillis(), "delete-hook-timeout");
  }

  private static void requirePositive(long value, String property) {
    if (value < 1) {
      throw new IllegalStateException(PREFIX + "." + property + " must be positive");
    }
  }

  public RecordLimits toLimits() {
    return new RecordLimits(records.getMaxDocumentBytes(), records.getMaxPerExtension());
  }

  public Duration getDeleteHookTimeout() {
    return deleteHookTimeout;
  }

  public void setDeleteHookTimeout(Duration deleteHookTimeout) {
    this.deleteHookTimeout = deleteHookTimeout;
  }

  public Records getRecords() {
    return records;
  }

  public void setRecords(Records records) {
    this.records = records;
  }

  /** What one extension may store on one connection. */
  public static class Records {

    private static final long DEFAULT_MAX_DOCUMENT_BYTES = 64L * 1024;
    private static final int DEFAULT_MAX_PER_EXTENSION = 1_000;

    private long maxDocumentBytes = DEFAULT_MAX_DOCUMENT_BYTES;
    private int maxPerExtension = DEFAULT_MAX_PER_EXTENSION;

    public long getMaxDocumentBytes() {
      return maxDocumentBytes;
    }

    public void setMaxDocumentBytes(long maxDocumentBytes) {
      this.maxDocumentBytes = maxDocumentBytes;
    }

    public int getMaxPerExtension() {
      return maxPerExtension;
    }

    public void setMaxPerExtension(int maxPerExtension) {
      this.maxPerExtension = maxPerExtension;
    }
  }
}
