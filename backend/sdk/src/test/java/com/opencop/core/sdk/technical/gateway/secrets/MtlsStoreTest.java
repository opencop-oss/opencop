// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.ports.gateway.secrets.SecretStore;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MtlsStoreTest {

  private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
  private static final UUID GENERATION_ID = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
  private static final String SOURCE_PREFIX = "opencop/shared/connect/" + SOURCE_ID;
  private static final String GENERATION_PREFIX = SOURCE_PREFIX + "/" + GENERATION_ID;
  private static final byte[] STORE = "store-bytes".getBytes(StandardCharsets.UTF_8);
  private static final String STORE_SHA256 =
      "c994833ef36d7d5310dd9866ad749998eaa9a1b8af78ba94b5fdadf48ac0d007";

  private final SecretStore secretStore = mock(SecretStore.class);
  private final MtlsStore store = new MtlsStore(secretStore);

  @Test
  void storesTheClientCertificateUnderItsGeneration() {
    var ref = store.saveClientCerts(SOURCE_ID, GENERATION_ID, stream(), "secret");

    assertThat(ref).isEqualTo(GENERATION_PREFIX + "/client");
    verify(secretStore).patch(eq(ref), eq(stored("secret")), eq(null));
  }

  @Test
  void storesTheTruststoreUnderItsGeneration() {
    var ref = store.saveTruststoreCerts(SOURCE_ID, GENERATION_ID, stream(), null);

    assertThat(ref).isEqualTo(GENERATION_PREFIX + "/truststore");
    verify(secretStore).patch(eq(ref), eq(stored("")), eq(null));
  }

  /** A later provisioning of the same source must never write where a live one reads. */
  @Test
  void neverStoresTwoGenerationsAtTheSamePath() {
    var first = store.saveClientCerts(SOURCE_ID, GENERATION_ID, stream(), "secret");
    var second = store.saveClientCerts(SOURCE_ID, UUID.randomUUID(), stream(), "secret");

    assertThat(second).isNotEqualTo(first);
  }

  /**
   * A write can fail after the secret store applied it, such as on a timeout, and then no caller
   * learns the ref to delete it by.
   */
  @Test
  void deletesWhatAFailedWriteMayHaveLeftAtItsPath() {
    var failure = new IllegalStateException("write timed out");
    when(secretStore.patch(anyString(), any(), any())).thenThrow(failure);

    assertThatThrownBy(() -> store.saveClientCerts(SOURCE_ID, GENERATION_ID, stream(), "secret"))
        .hasCause(failure);

    verify(secretStore).deleteAllVersions(GENERATION_PREFIX + "/client");
  }

  @Test
  void reportsTheFailedWriteEvenWhenItsCleanupAlsoFails() {
    var failure = new IllegalStateException("write timed out");
    var cleanupFailure = new IllegalStateException("delete timed out");
    when(secretStore.patch(anyString(), any(), any())).thenThrow(failure);
    doThrow(cleanupFailure).when(secretStore).deleteAllVersions(anyString());

    assertThatThrownBy(() -> store.saveClientCerts(SOURCE_ID, GENERATION_ID, stream(), "secret"))
        .hasCause(failure)
        .hasSuppressedException(cleanupFailure);
  }

  @Test
  void deletesExactlyTheRefsItIsGiven() {
    var client = GENERATION_PREFIX + "/client";
    var truststore = GENERATION_PREFIX + "/truststore";

    store.delete(List.of(client, truststore));

    verify(secretStore).deleteAllVersions(client);
    verify(secretStore).deleteAllVersions(truststore);
    verifyNoMoreInteractions(secretStore);
  }

  @Test
  void attemptsEveryRefAndReportsEveryFailureWhenDeletesFail() {
    var client = GENERATION_PREFIX + "/client";
    var truststore = GENERATION_PREFIX + "/truststore";
    var clientFailure = new IllegalStateException("client delete failed");
    var truststoreFailure = new IllegalStateException("truststore delete failed");
    doThrow(clientFailure).when(secretStore).deleteAllVersions(client);
    doThrow(truststoreFailure).when(secretStore).deleteAllVersions(truststore);

    assertThatThrownBy(() -> store.delete(List.of(client, truststore)))
        .isSameAs(clientFailure)
        .hasSuppressedException(truststoreFailure);

    verify(secretStore).deleteAllVersions(client);
    verify(secretStore).deleteAllVersions(truststore);
  }

  @Test
  void attemptsEveryLegacyPathWhenOneFails() {
    var credentials = SOURCE_PREFIX + "/credentials";
    var failure = new IllegalStateException("credentials delete failed");
    doThrow(failure).when(secretStore).deleteAllVersions(credentials);

    assertThatThrownBy(() -> store.deleteLegacy(SOURCE_ID)).isSameAs(failure);

    verify(secretStore).deleteAllVersions(SOURCE_PREFIX + "/client");
    verify(secretStore).deleteAllVersions(SOURCE_PREFIX + "/truststore");
  }

  @Test
  void deletesNothingWhenGivenNoRefs() {
    store.delete(List.of());

    verify(secretStore, never()).deleteAllVersions(anyString());
  }

  /** Connections created before generations stored their material at fixed per-source paths. */
  @Test
  void deletesTheFixedPathsOfALegacyConnection() {
    store.deleteLegacy(SOURCE_ID);

    verify(secretStore).deleteAllVersions(SOURCE_PREFIX + "/client");
    verify(secretStore).deleteAllVersions(SOURCE_PREFIX + "/truststore");
    verify(secretStore).deleteAllVersions(SOURCE_PREFIX + "/credentials");
    verifyNoMoreInteractions(secretStore);
  }

  private static ByteArrayInputStream stream() {
    return new ByteArrayInputStream(STORE);
  }

  private static Map<String, String> stored(String password) {
    return Map.of(
        "b64",
        Base64.getEncoder().encodeToString(STORE),
        "password",
        password,
        "sha256",
        STORE_SHA256);
  }
}
