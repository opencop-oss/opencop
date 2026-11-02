// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.security.session;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.session.servlet.cookie")
public class SessionCookieProperties {

  private String name;
  private String path;
  private String domain;
  private String sameSite;
  private Boolean secure;
  private Boolean httpOnly;

  // getters + setters

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String getDomain() {
    return domain;
  }

  public void setDomain(String domain) {
    this.domain = domain;
  }

  public String getSameSite() {
    return sameSite;
  }

  public void setSameSite(String sameSite) {
    this.sameSite = sameSite;
  }

  public Boolean getSecure() {
    return secure;
  }

  public void setSecure(Boolean secure) {
    this.secure = secure;
  }

  public Boolean getHttpOnly() {
    return httpOnly;
  }

  public void setHttpOnly(Boolean httpOnly) {
    this.httpOnly = httpOnly;
  }
}
