// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.connection;

import com.opencop.core.sdk.ports.gateway.secrets.SecretStore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A secret store kept in memory, standing in for Vault in component tests. It refuses the patches
 * Vault refuses, so a test cannot pass on a write the real store would reject.
 */
final class InMemorySecretStore implements SecretStore {

  private final Map<String, Map<String, String>> entries = new ConcurrentHashMap<>();

  @Override
  public Map<String, String> read(String ref) {
    return Map.copyOf(entries.getOrDefault(ref, Map.of()));
  }

  @Override
  public Map<String, String> patch(
      String ref, Map<String, String> upserts, Set<String> removeKeys) {
    if (upserts.isEmpty() && removeKeys.isEmpty()) {
      throw new IllegalArgumentException("patch must include upserts and/or removeKeys");
    }
    var merged = new LinkedHashMap<>(entries.getOrDefault(ref, Map.of()));
    removeKeys.forEach(merged::remove);
    merged.putAll(upserts);
    if (merged.isEmpty()) {
      throw new IllegalStateException(
          "Patch would result in empty secret document; refusing for ref=" + ref);
    }
    entries.put(ref, Map.copyOf(merged));
    return Map.copyOf(merged);
  }

  @Override
  public void deleteAllVersions(String ref) {
    entries.remove(ref);
  }

  boolean holds(String ref) {
    return entries.containsKey(ref);
  }

  void reset() {
    entries.clear();
  }
}
