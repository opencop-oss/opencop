// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.technical.gateway.db;

import java.util.UUID;

public interface LayerCopyGateway {

  void copyLayer(UUID sourceLayerId, UUID targetLayerId, boolean copyRemarks, boolean copyMedia);

  boolean targetHasFeatures(UUID targetLayerId);
}
