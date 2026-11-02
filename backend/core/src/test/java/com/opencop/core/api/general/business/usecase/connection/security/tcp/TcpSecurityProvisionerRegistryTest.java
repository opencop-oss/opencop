// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.security.tcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.connection.TcpTimeouts;
import com.opencop.core.sdk.business.model.integration.connection.material.certificate.CertificateMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.certificate.CertificateStoreFormat;
import com.opencop.core.sdk.business.model.integration.connection.material.certificate.DetachedBinaryMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.MtlsCredentialMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.NoSecurityMaterial;
import com.opencop.core.sdk.model.connection.spec.security.KeystoreRef;
import com.opencop.core.sdk.model.connection.spec.security.MtlsConfig;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.ports.gateway.secrets.ConnectionStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TcpSecurityProvisionerRegistryTest {

  private static final UUID SOURCE_ID = UUID.fromString("39df121e-086d-4618-87a3-eae856b6740a");

  private final ConnectionStore connectionStore = mock(ConnectionStore.class);
  private final TcpSecurityProvisionerRegistry provisioners =
      TcpSecurityProvisionerRegistry.standard(connectionStore);

  @Test
  void aPlainConnectionStoresNothingAndUsesTheDefaultTimeouts() {
    var provisioned = provisioners.provision(SOURCE_ID, new NoSecurityMaterial());

    assertThat(provisioned.security()).isEqualTo(new NoSecurity());
    assertThat(provisioned.timeouts()).isEqualTo(TcpTimeouts.DEFAULT);
    assertThat(provisioned.cleanup()).isEmpty();
    verifyNoInteractions(connectionStore);
  }

  @Test
  void aClientOnlyMtlsConnectionStoresItsClientAndUsesClientOnlyTimeouts() {
    when(connectionStore.saveClientCerts(eq(SOURCE_ID), any(), any(), eq("client-password")))
        .thenReturn("client-ref");

    var provisioned =
        provisioners.provision(
            SOURCE_ID,
            new MtlsCredentialMaterial(certificate("client-password"), Optional.empty()));

    // A client-only connection keeps a truststore reference without a URI.
    assertThat(provisioned.security()).isEqualTo(mtls("client-ref", null));
    assertThat(provisioned.timeouts()).isEqualTo(new TcpTimeouts(10_000, 0));
    verify(connectionStore, never()).saveTruststoreCerts(any(), any(), any(), any());
  }

  @Test
  void anMtlsConnectionWithACaStoresBothAndUsesTheDefaultTimeouts() {
    when(connectionStore.saveClientCerts(eq(SOURCE_ID), any(), any(), eq("client-password")))
        .thenReturn("client-ref");
    when(connectionStore.saveTruststoreCerts(
            eq(SOURCE_ID), any(), any(), eq("truststore-password")))
        .thenReturn("truststore-ref");

    var provisioned =
        provisioners.provision(
            SOURCE_ID,
            new MtlsCredentialMaterial(
                certificate("client-password"), Optional.of(certificate("truststore-password"))));

    assertThat(provisioned.security()).isEqualTo(mtls("client-ref", "truststore-ref"));
    assertThat(provisioned.timeouts()).isEqualTo(TcpTimeouts.DEFAULT);
  }

  /** Both stores of one provisioning share its generation, and no other provisioning reuses it. */
  @Test
  void storesBothCertificatesOfOneProvisioningUnderAFreshGeneration() {
    when(connectionStore.saveClientCerts(any(), any(), any(), any())).thenReturn("client-ref");
    when(connectionStore.saveTruststoreCerts(any(), any(), any(), any()))
        .thenReturn("truststore-ref");
    var clientGeneration = ArgumentCaptor.forClass(UUID.class);
    var truststoreGeneration = ArgumentCaptor.forClass(UUID.class);
    var material =
        new MtlsCredentialMaterial(
            certificate("client-password"), Optional.of(certificate("truststore-password")));

    provisioners.provision(SOURCE_ID, material);
    provisioners.provision(SOURCE_ID, material);

    verify(connectionStore, times(2))
        .saveClientCerts(eq(SOURCE_ID), clientGeneration.capture(), any(), any());
    verify(connectionStore, times(2))
        .saveTruststoreCerts(eq(SOURCE_ID), truststoreGeneration.capture(), any(), any());
    assertThat(truststoreGeneration.getAllValues()).isEqualTo(clientGeneration.getAllValues());
    assertThat(clientGeneration.getAllValues()).doesNotHaveDuplicates();
  }

  @Test
  void anMtlsConnectionsCleanupDeletesExactlyTheStoresItWrote() {
    when(connectionStore.saveClientCerts(any(), any(), any(), any())).thenReturn("client-ref");
    when(connectionStore.saveTruststoreCerts(any(), any(), any(), any()))
        .thenReturn("truststore-ref");

    var provisioned =
        provisioners.provision(
            SOURCE_ID,
            new MtlsCredentialMaterial(
                certificate("client-password"), Optional.of(certificate("truststore-password"))));
    provisioned.cleanup().orElseThrow().run();

    verify(connectionStore).delete(List.of("client-ref", "truststore-ref"));
    verify(connectionStore, never()).deleteLegacy(any());
  }

  @Test
  void aFailureWhileStoringDeletesWhatWasAlreadyStored() {
    var failure = new IllegalStateException("truststore write failed");
    when(connectionStore.saveClientCerts(any(), any(), any(), any())).thenReturn("client-ref");
    when(connectionStore.saveTruststoreCerts(any(), any(), any(), any())).thenThrow(failure);
    var material =
        new MtlsCredentialMaterial(
            certificate("client-password"), Optional.of(certificate("truststore-password")));

    assertThatThrownBy(() -> provisioners.provision(SOURCE_ID, material)).isSameAs(failure);

    verify(connectionStore).delete(List.of("client-ref"));
    verify(connectionStore, never()).deleteLegacy(any());
  }

  private static MtlsConfig mtls(String clientReference, String truststoreReference) {
    return new MtlsConfig(
        null,
        null,
        false,
        new KeystoreRef(truststoreReference, "PKCS12"),
        new KeystoreRef(clientReference, "PKCS12"));
  }

  private static CertificateMaterial certificate(String password) {
    return new CertificateMaterial(
        new DetachedBinaryMaterial(new byte[] {1}), password, CertificateStoreFormat.PKCS12);
  }
}
