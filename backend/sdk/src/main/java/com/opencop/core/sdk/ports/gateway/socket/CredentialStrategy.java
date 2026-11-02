// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.socket;

public enum CredentialStrategy {
  CERTS("certs"),
  USERNAME_PASSWORD("username+password"),
  NONE("none");

  private final String method;

  CredentialStrategy(String method) {
    this.method = method;
  }

  public String getMethod() {
    return this.method;
  }
}
