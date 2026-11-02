// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.security.tcp;

import com.opencop.core.sdk.business.model.integration.connection.material.tcp.TcpConnectionMaterial;
import com.opencop.core.sdk.business.policy.integration.connection.material.MaterialValidationPolicy;
import com.opencop.core.sdk.business.policy.integration.connection.material.certificate.CertificateMaterialPolicy;
import com.opencop.core.sdk.business.policy.integration.connection.material.tcp.TcpConnectionMaterialPolicy;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputException;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputFailure;

/** The TCP material rules every material source applies. */
public final class TcpMaterialPolicies {

  public static final MaterialValidationPolicy<TcpConnectionMaterial> MATERIAL =
      new TcpConnectionMaterialPolicy(new CertificateMaterialPolicy());

  private TcpMaterialPolicies() {}

  /**
   * Validates produced material, reporting a violation as the given failure: invalid input when the
   * caller supplied the material, an invalid result when an extractor produced it.
   */
  public static TcpConnectionMaterial validated(
      TcpConnectionMaterial material, ConnectionInputFailure failure)
      throws ConnectionInputException {
    try {
      MATERIAL.validate(material);
    } catch (RuntimeException exception) {
      throw new ConnectionInputException(
          failure, null, "Invalid TCP connection material", exception);
    }
    return material;
  }
}
