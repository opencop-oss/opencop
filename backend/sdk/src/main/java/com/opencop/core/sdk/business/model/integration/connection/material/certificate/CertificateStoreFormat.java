// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.material.certificate;

/** Certificate-store formats supported by the Core connection runtime. */
@SuppressWarnings("java:S6548") // Closed format catalog, not an enum-based singleton service.
public enum CertificateStoreFormat {
  PKCS12;

  public String runtimeName() {
    return name();
  }
}
