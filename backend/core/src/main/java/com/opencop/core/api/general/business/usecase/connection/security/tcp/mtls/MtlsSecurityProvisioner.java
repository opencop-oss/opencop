// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.security.tcp.mtls;

import com.opencop.core.api.general.business.model.connection.TcpTimeouts;
import com.opencop.core.api.general.business.usecase.connection.security.tcp.ProvisionedTcpSecurity;
import com.opencop.core.api.general.business.usecase.connection.security.tcp.TcpSecurityProvisioner;
import com.opencop.core.sdk.business.model.integration.connection.material.certificate.CertificateMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.certificate.CertificateStoreFormat;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.MtlsCredentialMaterial;
import com.opencop.core.sdk.model.connection.spec.security.KeystoreRef;
import com.opencop.core.sdk.model.connection.spec.security.MtlsConfig;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.secrets.ConnectionStore;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * An mTLS connection: stores the client keystore, and the truststore when a CA is supplied, then
 * references them from the connection's security.
 *
 * <p>Each provisioning stores its material under a generation of its own, so it never overwrites
 * material another provisioning of the same source stored, and its compensation deletes only what
 * it stored itself.
 */
public final class MtlsSecurityProvisioner
    implements TcpSecurityProvisioner<MtlsCredentialMaterial> {

  private static final Logger log = LoggerFactory.getLogger(MtlsSecurityProvisioner.class);

  // The timeouts a client-only connection, one without a CA, is created with.
  private static final TcpTimeouts CLIENT_ONLY_TIMEOUTS = new TcpTimeouts(10_000, 0);
  // The truststore type a client-only connection stores, where no CA certificate names one.
  private static final String DEFAULT_TRUSTSTORE_TYPE = CertificateStoreFormat.PKCS12.runtimeName();

  private final ConnectionStore connectionStore;

  public MtlsSecurityProvisioner(ConnectionStore connectionStore) {
    this.connectionStore = connectionStore;
  }

  @Override
  public ProvisionedTcpSecurity provision(UUID sourceId, MtlsCredentialMaterial material) {
    var generationId = UUID.randomUUID();
    var stored = new ArrayList<String>();
    MtlsConfig security;
    try {
      security = store(sourceId, generationId, material, stored);
    } catch (RuntimeException failure) {
      deleteStored(sourceId, stored, failure);
      throw failure;
    }
    var refs = List.copyOf(stored);
    return new ProvisionedTcpSecurity(
        security, timeouts(material), Optional.of(() -> connectionStore.delete(refs)));
  }

  /**
   * @param stored collects each ref as soon as its store is written, so a failure part way through
   *     still knows what to delete
   */
  private MtlsConfig store(
      UUID sourceId, UUID generationId, MtlsCredentialMaterial material, List<String> stored) {
    var client = material.clientCertificate();
    var clientRef =
        save(
            client,
            content ->
                connectionStore.saveClientCerts(
                    sourceId, generationId, content, client.password()));
    stored.add(clientRef);
    var clientStore = new KeystoreRef(clientRef, client.format().runtimeName());
    // Protocols and ciphers stay unset so the runtime defaults apply.
    return new MtlsConfig(
        null, null, false, trustStore(sourceId, generationId, material, stored), clientStore);
  }

  /** The stored CA as the truststore; a client-only connection keeps a reference without a URI. */
  private KeystoreRef trustStore(
      UUID sourceId, UUID generationId, MtlsCredentialMaterial material, List<String> stored) {
    if (material.caCertificate().isEmpty()) {
      return new KeystoreRef(null, DEFAULT_TRUSTSTORE_TYPE);
    }
    var ca = material.caCertificate().orElseThrow();
    var truststoreRef =
        save(
            ca,
            content ->
                connectionStore.saveTruststoreCerts(
                    sourceId, generationId, content, ca.password()));
    stored.add(truststoreRef);
    return new KeystoreRef(truststoreRef, ca.format().runtimeName());
  }

  private static TcpTimeouts timeouts(MtlsCredentialMaterial material) {
    if (material.caCertificate().isEmpty()) {
      return CLIENT_ONLY_TIMEOUTS;
    }
    return TcpTimeouts.DEFAULT;
  }

  private static String save(CertificateMaterial certificate, StoreCall call) {
    try (var content = certificate.content().openStream()) {
      return call.store(content);
    } catch (IOException _) {
      throw new GlobalOpenCopException(
          "Certificate file reading error", HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  private void deleteStored(UUID sourceId, List<String> stored, RuntimeException failure) {
    try {
      connectionStore.delete(stored);
    } catch (RuntimeException cleanupFailure) {
      failure.addSuppressed(cleanupFailure);
      log.error("Failed to delete incomplete credentials for source {}", sourceId, cleanupFailure);
    }
  }

  @FunctionalInterface
  private interface StoreCall {
    String store(InputStream content);
  }
}
