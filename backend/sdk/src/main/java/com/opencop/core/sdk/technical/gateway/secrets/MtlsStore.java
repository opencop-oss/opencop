// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.secrets;

import com.opencop.core.sdk.ports.gateway.secrets.ConnectionStore;
import com.opencop.core.sdk.ports.gateway.secrets.SecretStore;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MtlsStore implements ConnectionStore {

  private static final String SOURCE_ROOT = "opencop/shared/connect/%s";
  private static final String CLIENT_PATH = SOURCE_ROOT + "/%s/client";
  private static final String TRUSTSTORE_PATH = SOURCE_ROOT + "/%s/truststore";
  private static final String LEGACY_CLIENT_PATH = SOURCE_ROOT + "/client";
  private static final String LEGACY_TRUSTSTORE_PATH = SOURCE_ROOT + "/truststore";
  private static final String LEGACY_CREDENTIALS_PATH = SOURCE_ROOT + "/credentials";

  private final SecretStore secretStore;

  MtlsStore(SecretStore secretStore) {
    this.secretStore = secretStore;
  }

  @Override
  public Map<String, String> read(String ref) {
    return secretStore.read(ref);
  }

  @Override
  public String saveClientCerts(
      UUID sourceId, UUID generationId, InputStream p12, String p12Password) {
    var ref = CLIENT_PATH.formatted(sourceId, generationId);
    this.saveCerts(ref, p12, p12Password);
    return ref;
  }

  @Override
  public String saveTruststoreCerts(
      UUID sourceId, UUID generationId, InputStream p12, String p12Password) {
    var ref = TRUSTSTORE_PATH.formatted(sourceId, generationId);
    this.saveCerts(ref, p12, p12Password);
    return ref;
  }

  private void saveCerts(String ref, InputStream p12, String p12Password) {
    try {
      byte[] bytes = p12.readAllBytes();
      String b64 = Base64.getEncoder().encodeToString(bytes);

      String sha256 = sha256Hex(bytes);

      secretStore.patch(
          ref,
          Map.of(
              "b64", b64,
              "password", p12Password == null ? "" : p12Password,
              "sha256", sha256),
          null);
    } catch (Exception e) {
      var failure = new IllegalStateException("Failed to store p12 at ref=" + ref, e);
      deleteFailedWrite(ref, failure);
      throw failure;
    }
  }

  /**
   * A write can fail after the secret store applied it, such as on a timeout, and the caller never
   * learns the ref, so this is the only place that can still delete it.
   */
  private void deleteFailedWrite(String ref, IllegalStateException failure) {
    try {
      secretStore.deleteAllVersions(ref);
    } catch (RuntimeException cleanupFailure) {
      failure.addSuppressed(cleanupFailure);
    }
  }

  /**
   * Attempts every ref even when an earlier one fails, so one failure does not strand the rest; the
   * first failure is thrown once all were attempted, carrying the others as suppressed.
   */
  @Override
  public void delete(Collection<String> refs) {
    RuntimeException failure = null;
    for (var ref : refs) {
      try {
        secretStore.deleteAllVersions(ref);
      } catch (RuntimeException e) {
        if (failure == null) {
          failure = e;
        } else {
          failure.addSuppressed(e);
        }
      }
    }
    if (failure != null) {
      throw failure;
    }
  }

  @Override
  public void deleteLegacy(UUID sourceId) {
    delete(
        List.of(
            LEGACY_CREDENTIALS_PATH.formatted(sourceId),
            LEGACY_CLIENT_PATH.formatted(sourceId),
            LEGACY_TRUSTSTORE_PATH.formatted(sourceId)));
  }

  private static String sha256Hex(byte[] bytes) throws NoSuchAlgorithmException {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    byte[] digest = md.digest(bytes);
    return HexFormat.of().formatHex(digest);
  }
}
