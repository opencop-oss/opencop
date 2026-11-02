// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.connection.certificate;

import com.opencop.core.api.general.ports.gateway.connection.certificate.CertificateStoreGateway;
import com.opencop.core.sdk.business.model.integration.connection.material.certificate.CertificateMaterial;
import com.opencop.core.sdk.technical.gateway.extractor.CertificateStoreOpening;
import java.io.IOException;
import org.springframework.stereotype.Component;

/**
 * Opens a store with the Java key store of its format, as Connect does at its handshake. A store
 * declaring more key derivation than {@link CertificateStoreOpening#MAX_OPENING_ITERATIONS} is
 * refused without being opened.
 */
@Component
public class KeyStoreCertificateStoreGateway implements CertificateStoreGateway {

  @Override
  public boolean opensWithItsPassword(CertificateMaterial certificate) {
    try (var content = certificate.content().openStream()) {
      return CertificateStoreOpening.opens(
          content.readAllBytes(), certificate.password(), certificate.format());
    } catch (IOException _) {
      return false;
    }
  }
}
