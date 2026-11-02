// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.ports.gateway;

import java.util.UUID;

public interface WatchboxMapperIdentity {

  boolean hasStableObjectId(UUID mapperId);
}
