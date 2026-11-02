// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.policy.integration.connection.material.certificate;

import com.opencop.core.sdk.business.model.integration.connection.material.certificate.CertificateMaterial;
import com.opencop.core.sdk.business.policy.integration.connection.material.MaterialValidationPolicy;

/** Validates detached certificate-store material independently of its source or consumer. */
public final class CertificateMaterialPolicy
    implements MaterialValidationPolicy<CertificateMaterial> {

  @Override
  public void validate(CertificateMaterial material) {
    if (material == null) {
      throw new IllegalArgumentException("certificate material must not be null");
    }
    if (material.content() == null || material.content().byteSize() == 0) {
      throw new IllegalArgumentException("certificate content must not be empty");
    }
    if (material.password() == null || material.password().isBlank()) {
      throw new IllegalArgumentException("certificate password must not be blank");
    }
    if (material.format() == null) {
      throw new IllegalArgumentException("certificate format must not be null");
    }
  }
}
