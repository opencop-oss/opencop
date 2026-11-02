// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.material.tcp;

import com.opencop.core.sdk.business.model.integration.connection.material.certificate.CertificateMaterial;
import java.util.Objects;
import java.util.Optional;

/** Required client certificate material and an optional paired CA/truststore value. */
public record MtlsCredentialMaterial(
    CertificateMaterial clientCertificate, Optional<CertificateMaterial> caCertificate)
    implements TcpSecurityMaterial {

  public MtlsCredentialMaterial {
    Objects.requireNonNull(clientCertificate, "clientCertificate");
    Objects.requireNonNull(caCertificate, "caCertificate");
  }

  @Override
  public String toString() {
    return "MtlsCredentialMaterial[clientCertificatePresent=true, caCertificatePresent=%s]"
        .formatted(caCertificate.isPresent());
  }
}
