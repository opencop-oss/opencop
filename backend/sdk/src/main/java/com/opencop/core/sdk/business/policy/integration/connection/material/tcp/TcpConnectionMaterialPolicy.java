// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.policy.integration.connection.material.tcp;

import com.opencop.core.sdk.business.model.integration.connection.material.certificate.CertificateMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.MtlsCredentialMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.NoSecurityMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.TcpConnectionMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.TcpEndpointMaterial;
import com.opencop.core.sdk.business.policy.integration.connection.material.MaterialValidationPolicy;
import java.util.Objects;

/**
 * Validates complete TCP material independently of how it was supplied or where it will be used.
 */
public final class TcpConnectionMaterialPolicy
    implements MaterialValidationPolicy<TcpConnectionMaterial> {

  private final MaterialValidationPolicy<CertificateMaterial> certificatePolicy;

  public TcpConnectionMaterialPolicy(
      MaterialValidationPolicy<CertificateMaterial> certificatePolicy) {
    this.certificatePolicy = Objects.requireNonNull(certificatePolicy, "certificatePolicy");
  }

  @Override
  public void validate(TcpConnectionMaterial material) {
    if (material == null) {
      throw new IllegalArgumentException("TCP connection material must not be null");
    }
    validateEndpoint(material.endpoint());
    switch (material.security()) {
      case NoSecurityMaterial _ -> {
        // A plain connection carries nothing beyond its endpoint.
      }
      case MtlsCredentialMaterial credentials -> validateCredentials(credentials);
    }
  }

  private static void validateEndpoint(TcpEndpointMaterial endpoint) {
    if (endpoint.host() == null || endpoint.host().isBlank()) {
      throw new IllegalArgumentException("host must not be blank");
    }
    if (endpoint.port() < 1 || endpoint.port() > 65_535) {
      throw new IllegalArgumentException("port must be between 1 and 65535");
    }
  }

  private void validateCredentials(MtlsCredentialMaterial credentials) {
    certificatePolicy.validate(credentials.clientCertificate());
    credentials.caCertificate().ifPresent(certificatePolicy::validate);
  }
}
