// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.secrets;

import java.util.Map;
import java.util.Set;

public interface SecretStore {

  Map<String, String> read(String ref);

  Map<String, String> patch(String ref, Map<String, String> upserts, Set<String> removeKeys);

  void deleteAllVersions(String ref);
}
