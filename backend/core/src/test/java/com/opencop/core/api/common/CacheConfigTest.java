// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.technical.gateway.profile.MapperProfileCaches;
import org.junit.jupiter.api.Test;

class CacheConfigTest {

  @Test
  void cacheManagerRegistersMapperProfileCaches() {
    var cacheManager = new CacheConfig().cacheManager();

    assertThat(cacheManager.getCacheNames())
        .contains(
            MapperProfileCaches.BY_SOURCE_ID,
            MapperProfileCaches.BY_MAPPER_ID,
            MapperProfileCaches.SOURCE_ONLY_WRITES)
        .doesNotContain("adminFlag");
  }
}
