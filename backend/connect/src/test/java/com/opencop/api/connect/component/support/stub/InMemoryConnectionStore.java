// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.component.support.stub;

import com.opencop.core.sdk.ports.gateway.secrets.ConnectionStore;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public final class InMemoryConnectionStore implements ConnectionStore {

  private final Map<String, Map<String, String>> entries;

  public InMemoryConnectionStore(Map<String, Map<String, String>> entries) {
    this.entries = Map.copyOf(entries);
  }

  @Override
  public Map<String, String> read(String ref) {
    return entries.get(ref);
  }

  @Override
  public String saveClientCerts(
      UUID sourceId, UUID generationId, InputStream p12, String p12Password) {
    throw unsupported();
  }

  @Override
  public String saveTruststoreCerts(
      UUID sourceId, UUID generationId, InputStream p12, String p12Password) {
    throw unsupported();
  }

  @Override
  public void delete(Collection<String> refs) {
    throw unsupported();
  }

  @Override
  public void deleteLegacy(UUID sourceId) {
    throw unsupported();
  }

  private UnsupportedOperationException unsupported() {
    return new UnsupportedOperationException("Not needed for this component test");
  }
}
