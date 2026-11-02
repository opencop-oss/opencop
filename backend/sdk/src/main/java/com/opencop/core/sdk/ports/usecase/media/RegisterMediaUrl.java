// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.usecase.media;

import com.opencop.core.sdk.model.media.MediaAccess;
import com.opencop.core.sdk.model.media.MediaUrlRecord;
import com.opencop.core.sdk.model.media.MediaUsage;
import com.opencop.core.sdk.model.media.StreamKind;
import java.util.UUID;

public interface RegisterMediaUrl {

  /**
   * @param requestedKind the transport the caller claims; {@code null} asks the implementation to
   *     infer it, which only succeeds for unambiguous URLs
   */
  MediaUrlRecord register(
      UUID userId, String url, StreamKind requestedKind, MediaAccess access, MediaUsage usage);
}
