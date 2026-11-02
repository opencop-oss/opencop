// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.model.connection.spec.security.KeystoreRef;
import com.opencop.core.sdk.model.connection.spec.security.MtlsConfig;
import com.opencop.core.sdk.ports.gateway.secrets.ConnectionStore;
import com.opencop.core.sdk.testing.connection.TestMtlsPki;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.Test;

class MtlsSslContextFactoryTest {

  private static final String CLIENT_REF = "opencop/shared/connect/source/generation/client";
  private static final String TRUSTSTORE_REF =
      "opencop/shared/connect/source/generation/truststore";
  private static final String STORE_TYPE = "PKCS12";
  private static final String CONTENT_KEY = "b64";
  private static final String PASSWORD_KEY = "password";
  private static final String PROTOCOL = "TLS";
  private static final String CLIENT_ALIAS = "client";
  private static final int HANDSHAKE_TIMEOUT_SECONDS = 10;

  private final TestMtlsPki pki = TestMtlsPki.shared();
  private final ConnectionStore connectionStore = mock(ConnectionStore.class);
  private final MtlsSslContextFactory factory = new MtlsSslContextFactory(connectionStore);

  @Test
  void contextFromClientAndTruststoreCompletesAMutualHandshake() throws Exception {
    storeClient(pki.clientKeyStoreBase64());
    storeTruststore(pki.trustStoreBase64());

    var context = factory.create(mtls(new KeystoreRef(TRUSTSTORE_REF, STORE_TYPE)));

    assertThat(handshakeWith(context)).isTrue();
  }

  @Test
  void contextWithoutTruststoreTrustsTheCaFromTheClientChain() throws Exception {
    storeClient(pki.clientKeyStoreBase64());

    var context = factory.create(mtls(new KeystoreRef(null, STORE_TYPE)));

    assertThat(handshakeWith(context)).isTrue();
  }

  @Test
  void truststoreRefWithoutStoredMaterialFallsBackToTheClientChain() throws Exception {
    storeClient(pki.clientKeyStoreBase64());

    var context = factory.create(mtls(new KeystoreRef(TRUSTSTORE_REF, STORE_TYPE)));

    assertThat(handshakeWith(context)).isTrue();
  }

  @Test
  void missingClientMaterialIsRejected() {
    var config = mtls(null);

    assertThatThrownBy(() -> factory.create(config))
        .isInstanceOf(MtlsMaterialException.class)
        .hasMessageContaining("client");
  }

  @Test
  void clientMaterialWithoutPasswordIsRejected() {
    when(connectionStore.read(CLIENT_REF))
        .thenReturn(Map.of(CONTENT_KEY, pki.clientKeyStoreBase64()));
    var config = mtls(null);

    assertThatThrownBy(() -> factory.create(config)).isInstanceOf(MtlsMaterialException.class);
  }

  @Test
  void malformedClientMaterialIsRejected() {
    storeClient("not base64 !");
    var config = mtls(null);

    assertThatThrownBy(() -> factory.create(config)).isInstanceOf(MtlsMaterialException.class);
  }

  @Test
  void clientChainWithoutAnAuthorityIsRejectedWhenNoTruststoreIsStored() throws Exception {
    storeClient(clientStoreWithoutAuthority());
    var config = mtls(null);

    assertThatThrownBy(() -> factory.create(config)).isInstanceOf(MtlsMaterialException.class);
  }

  @Test
  void malformedTruststoreMaterialIsRejected() {
    storeClient(pki.clientKeyStoreBase64());
    storeTruststore("not base64 !");
    var config = mtls(new KeystoreRef(TRUSTSTORE_REF, STORE_TYPE));

    assertThatThrownBy(() -> factory.create(config)).isInstanceOf(MtlsMaterialException.class);
  }

  @Test
  void referencedTruststoreWithAnUnknownTypeIsRejectedEvenWithoutStoredMaterial() {
    storeClient(pki.clientKeyStoreBase64());
    var config = mtls(new KeystoreRef(TRUSTSTORE_REF, "NOT-A-STORE-TYPE"));

    assertThatThrownBy(() -> factory.create(config)).isInstanceOf(MtlsMaterialException.class);
  }

  @Test
  void unreadableStoreIsReportedAsUnusableMaterial() {
    var unreachable = new IllegalStateException("store unreachable");
    when(connectionStore.read(CLIENT_REF)).thenThrow(unreachable);
    var config = mtls(null);

    assertThatThrownBy(() -> factory.create(config))
        .isInstanceOf(MtlsMaterialException.class)
        .hasCause(unreachable);
  }

  private void storeClient(String base64) {
    when(connectionStore.read(CLIENT_REF))
        .thenReturn(Map.of(CONTENT_KEY, base64, PASSWORD_KEY, pki.password()));
  }

  private void storeTruststore(String base64) {
    when(connectionStore.read(TRUSTSTORE_REF))
        .thenReturn(Map.of(CONTENT_KEY, base64, PASSWORD_KEY, pki.password()));
  }

  private static MtlsConfig mtls(KeystoreRef trustStore) {
    return new MtlsConfig(null, null, false, trustStore, new KeystoreRef(CLIENT_REF, STORE_TYPE));
  }

  /** The client's own store, re-written with only its leaf certificate in the chain. */
  private String clientStoreWithoutAuthority() throws Exception {
    var password = pki.password().toCharArray();
    var original = load(pki.clientKeyStore());
    var key = (PrivateKey) original.getKey(CLIENT_ALIAS, password);
    var leaf = original.getCertificateChain(CLIENT_ALIAS)[0];
    var stripped = KeyStore.getInstance(STORE_TYPE);
    stripped.load(null, null);
    stripped.setKeyEntry(CLIENT_ALIAS, key, password, new Certificate[] {leaf});
    var content = new ByteArrayOutputStream();
    stripped.store(content, password);
    return Base64.getEncoder().encodeToString(content.toByteArray());
  }

  /**
   * Handshakes against a loopback server that presents the test server identity and requires a
   * client certificate signed by the test authority, so success proves both directions of trust.
   */
  private boolean handshakeWith(SSLContext clientContext) throws Exception {
    try (var server =
        (SSLServerSocket)
            serverContext()
                .getServerSocketFactory()
                .createServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      server.setNeedClientAuth(true);
      var accepted =
          CompletableFuture.supplyAsync(
              () -> {
                // A failed handshake fails the wait below with its own cause.
                try (var socket = (SSLSocket) server.accept()) {
                  socket.startHandshake();
                  return socket.getSession().getPeerCertificates().length > 0;
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              });
      try (var client =
          (SSLSocket)
              clientContext
                  .getSocketFactory()
                  .createSocket(InetAddress.getLoopbackAddress(), server.getLocalPort())) {
        client.startHandshake();
      }
      return accepted.get(HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
  }

  private SSLContext serverContext() throws Exception {
    var password = pki.password().toCharArray();
    var keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keys.init(load(pki.serverKeyStore()), password);
    var trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trust.init(load(pki.trustStore()));
    var context = SSLContext.getInstance(PROTOCOL);
    context.init(keys.getKeyManagers(), trust.getTrustManagers(), null);
    return context;
  }

  private KeyStore load(InputStream content) throws Exception {
    try (content) {
      var store = KeyStore.getInstance(STORE_TYPE);
      store.load(content, pki.password().toCharArray());
      return store;
    }
  }
}
