// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.secrets;

import java.io.InputStream;
import java.util.Collection;
import java.util.UUID;

/**
 * Where a connection's certificate stores live. Each provisioning writes its own generation, so new
 * material never overwrites the material a running connection reads.
 */
public interface ConnectionStore extends SecretReader {

  /**
   * @param generationId the provisioning this store belongs to, fresh for every provisioning
   * @return the ref the connection's spec references the stored client certificate by
   */
  String saveClientCerts(UUID sourceId, UUID generationId, InputStream p12, String p12Password);

  /**
   * @param generationId the provisioning this store belongs to, fresh for every provisioning
   * @return the ref the connection's spec references the stored truststore by
   */
  String saveTruststoreCerts(UUID sourceId, UUID generationId, InputStream p12, String p12Password);

  /** Deletes every version of each ref, and nothing else. */
  void delete(Collection<String> refs);

  /**
   * Deletes the fixed per-source paths connections stored their material at before generations
   * existed.
   */
  void deleteLegacy(UUID sourceId);
}
