// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.material.certificate;

import java.util.Objects;

/** Detached certificate-store content paired with its interpretation and required password. */
public record CertificateMaterial(
    DetachedBinaryMaterial content, String password, CertificateStoreFormat format) {

  public CertificateMaterial {
    Objects.requireNonNull(content, "content");
    Objects.requireNonNull(format, "format");
  }

  @Override
  public String toString() {
    return "CertificateMaterial[contentByteSize=%d, passwordPresent=%s, format=%s]"
        .formatted(content.byteSize(), password != null, format);
  }
}
