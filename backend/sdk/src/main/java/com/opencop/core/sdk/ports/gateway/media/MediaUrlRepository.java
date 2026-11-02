// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.media;

import com.opencop.core.sdk.model.media.MediaUrlRecord;
import java.util.Optional;
import java.util.UUID;

public interface MediaUrlRepository {

  MediaUrlRecord save(MediaUrlRecord record);

  Optional<MediaUrlRecord> findById(UUID id);

  void deleteById(UUID id);
}
