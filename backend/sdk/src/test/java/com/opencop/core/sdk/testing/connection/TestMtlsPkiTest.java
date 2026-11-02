// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.testing.connection;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.junit.jupiter.api.Test;

class TestMtlsPkiTest {

  @Test
  void generatedStores_areRoleCorrect_andDefensivelyExposed() throws Exception {
    TestMtlsPki pki = TestMtlsPki.shared();
    KeyStore serverStore = load(pki.serverKeyStore(), pki.password());
    KeyStore clientStore = load(pki.clientKeyStore(), pki.password());
    KeyStore trustStore = load(pki.trustStore(), pki.password());

    assertTrue(serverStore.isKeyEntry("server"));
    assertTrue(clientStore.isKeyEntry("client"));
    assertTrue(trustStore.isCertificateEntry("ca"));

    Certificate[] serverChain = serverStore.getCertificateChain("server");
    Certificate[] clientChain = clientStore.getCertificateChain("client");
    assertNotNull(serverChain);
    assertNotNull(clientChain);
    assertEquals(2, serverChain.length);
    assertEquals(2, clientChain.length);

    X509Certificate server = (X509Certificate) serverChain[0];
    X509Certificate client = (X509Certificate) clientChain[0];
    X509Certificate ca = (X509Certificate) trustStore.getCertificate("ca");
    assertEquals(ca, serverChain[1]);
    assertEquals(ca, clientChain[1]);
    server.verify(ca.getPublicKey());
    client.verify(ca.getPublicKey());
    ca.verify(ca.getPublicKey());

    ca.checkValidity();
    server.checkValidity();
    client.checkValidity();
    assertTrue(ca.getBasicConstraints() >= 0);
    assertEquals(-1, server.getBasicConstraints());
    assertEquals(-1, client.getBasicConstraints());

    assertArrayEquals(
        new boolean[] {false, false, false, false, false, true, true, false, false},
        ca.getKeyUsage());
    assertArrayEquals(
        new boolean[] {true, false, true, false, false, false, false, false, false},
        server.getKeyUsage());
    assertArrayEquals(
        new boolean[] {true, false, false, false, false, false, false, false, false},
        client.getKeyUsage());
    assertEquals(List.of(KeyPurposeId.id_kp_serverAuth.getId()), server.getExtendedKeyUsage());
    assertEquals(List.of(KeyPurposeId.id_kp_clientAuth.getId()), client.getExtendedKeyUsage());

    Collection<List<?>> serverSubjectAlternativeNames = server.getSubjectAlternativeNames();
    assertNotNull(serverSubjectAlternativeNames);
    assertEquals(2, serverSubjectAlternativeNames.size());
    assertTrue(
        hasSubjectAlternativeName(serverSubjectAlternativeNames, GeneralName.dNSName, "localhost"));
    assertTrue(
        hasSubjectAlternativeName(
            serverSubjectAlternativeNames, GeneralName.iPAddress, "127.0.0.1"));
    assertNull(client.getSubjectAlternativeNames());

    assertFreshStreams(pki.serverKeyStore(), pki.serverKeyStore());
    assertFreshStreams(pki.clientKeyStore(), pki.clientKeyStore());
    assertFreshStreams(pki.trustStore(), pki.trustStore());
    assertStoreMatchesBase64(pki.clientKeyStore(), pki.clientKeyStoreBase64());
    assertStoreMatchesBase64(pki.trustStore(), pki.trustStoreBase64());
  }

  private static KeyStore load(InputStream input, String password) throws Exception {
    try (input) {
      KeyStore store = KeyStore.getInstance("PKCS12");
      store.load(input, password.toCharArray());
      return store;
    }
  }

  private static boolean hasSubjectAlternativeName(
      Collection<List<?>> names, int type, String value) {
    return names.stream()
        .anyMatch(name -> Integer.valueOf(type).equals(name.get(0)) && value.equals(name.get(1)));
  }

  private static void assertFreshStreams(InputStream first, InputStream second) throws Exception {
    assertNotSame(first, second);
    try (first;
        second) {
      assertArrayEquals(first.readAllBytes(), second.readAllBytes());
    }
  }

  private static void assertStoreMatchesBase64(InputStream store, String encoded) throws Exception {
    try (store) {
      assertArrayEquals(store.readAllBytes(), Base64.getDecoder().decode(encoded));
    }
  }
}
