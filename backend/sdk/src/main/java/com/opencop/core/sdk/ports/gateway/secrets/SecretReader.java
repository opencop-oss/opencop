// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.secrets;

import java.util.Map;

/** Reads stored secret material by the ref it was stored under. */
public interface SecretReader {

  /**
   * @return the material's entries, or null when nothing is stored under the ref
   */
  Map<String, String> read(String ref);
}
