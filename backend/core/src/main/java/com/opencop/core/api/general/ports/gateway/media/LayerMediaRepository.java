// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.media;

import java.util.UUID;

public interface LayerMediaRepository {

  boolean exists(UUID layerId, UUID mediaId);

  void save(UUID layerId, UUID mediaId);
}
