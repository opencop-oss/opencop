// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.media;

import java.util.Optional;

public interface MediaStreamGateway {

  /**
   * Registers a candidate on-demand HLS path for the given source and returns the candidate URL.
   */
  Optional<String> prepareStream(String pathName, String sourceUrl);
}
