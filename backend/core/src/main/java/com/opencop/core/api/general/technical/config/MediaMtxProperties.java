// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "opencop.mediamtx")
public class MediaMtxProperties {

  private boolean enabled = false;
  private Api api = new Api();
  private Hls hls = new Hls();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Api getApi() {
    return api;
  }

  public void setApi(Api api) {
    this.api = api;
  }

  public Hls getHls() {
    return hls;
  }

  public void setHls(Hls hls) {
    this.hls = hls;
  }

  public static class Api {

    private String baseUrl = "";
    private String username = "";
    private String password = "";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(5);

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }

    public Duration getConnectTimeout() {
      return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
      this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
      return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
      this.readTimeout = readTimeout;
    }
  }

  public static class Hls {

    private String publicBaseUrl = "/streams";

    public String getPublicBaseUrl() {
      return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
      this.publicBaseUrl = publicBaseUrl;
    }
  }
}
