// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.cache;

import java.util.UUID;

public interface EventGeoCacheEvictionGateway {

  void evictGeoSource(UUID geoSourceId);
}
