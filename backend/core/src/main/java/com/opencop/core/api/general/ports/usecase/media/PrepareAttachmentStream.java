// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.media;

import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.Optional;
import java.util.UUID;

public interface PrepareAttachmentStream {

  /**
   * Authorizes the caller, resolves the RTSP URL for the given attachment, and registers a
   * candidate HLS playback path in MediaMTX. Returns the candidate HLS URL, or empty if the
   * MediaMTX gateway is unavailable.
   */
  Optional<String> prepare(
      OpenCopPrincipal authentication, UUID sourceId, String objectId, UUID attachmentId);
}
