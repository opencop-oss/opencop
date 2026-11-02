// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.media;

import java.nio.charset.StandardCharsets;

final class SyntheticMediaFixtures {

  private SyntheticMediaFixtures() {}

  static byte[] svg() {
    return """
        <svg xmlns="http://www.w3.org/2000/svg" width="16" height="12" viewBox="0 0 16 12">
          <rect width="16" height="12" fill="#2457ff"/>
          <circle cx="8" cy="6" r="3" fill="#ffd43b"/>
        </svg>
        """
        .getBytes(StandardCharsets.UTF_8);
  }
}
