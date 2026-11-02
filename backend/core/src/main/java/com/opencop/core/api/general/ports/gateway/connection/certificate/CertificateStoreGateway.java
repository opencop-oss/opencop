// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.connection.certificate;

import com.opencop.core.sdk.business.model.integration.connection.material.certificate.CertificateMaterial;

/** Reads certificate stores the way a connection will when it first connects. */
public interface CertificateStoreGateway {

  /** Whether the store opens with the password it came with, in the format it declares. */
  boolean opensWithItsPassword(CertificateMaterial certificate);
}
