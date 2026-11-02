// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.blob.azure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "opencop.blob")
@ConditionalOnProperty(name = "opencop.cloud.provider", havingValue = "azure")
public class AzureProperties {

  private String containerName;
  private String accountName;
  private String accountKey;
  private String endpoint;
  private String publicEndpoint;
  private Media media = new Media();

  public String getContainerName() {
    return containerName;
  }

  public void setContainerName(String containerName) {
    this.containerName = containerName;
  }

  public String getAccountName() {
    return accountName;
  }

  public void setAccountName(String accountName) {
    this.accountName = accountName;
  }

  public String getAccountKey() {
    return accountKey;
  }

  public void setAccountKey(String accountKey) {
    this.accountKey = accountKey;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  public String getPublicEndpoint() {
    return publicEndpoint;
  }

  public void setPublicEndpoint(String publicEndpoint) {
    this.publicEndpoint = publicEndpoint;
  }

  public Media getMedia() {
    return media;
  }

  public void setMedia(Media media) {
    this.media = media;
  }

  /** Media-specific blob config under {@code opencop.blob.media.*}. */
  public static class Media {

    private String containerName = "opencop-common";
    private int sasExpiryMinutes = 10;

    public String getContainerName() {
      return containerName;
    }

    public void setContainerName(String containerName) {
      this.containerName = containerName;
    }

    public int getSasExpiryMinutes() {
      return sasExpiryMinutes;
    }

    public void setSasExpiryMinutes(int sasExpiryMinutes) {
      this.sasExpiryMinutes = sasExpiryMinutes;
    }
  }
}
