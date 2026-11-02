// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.watchbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.api.common.CacheConfig;
import com.opencop.core.api.component.general.profile.ProfileWebTestApplication;
import com.opencop.core.api.component.support.fixture.ProfileFixtures;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperProfileFullMeta;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.database.AbstractDatabaseIT;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.technical.gateway.profile.MapperProfileCaches;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the stale-cache premise that Watchbox admission must avoid: {@code findLatestForSource}
 * converges to a new mapper version only within its Caffeine TTL, while the uncached latest
 * revision of the connection's own mapper is visible immediately.
 */
@ComponentWebIT(classes = {ProfileWebTestApplication.class, CacheConfig.class})
class WatchboxMapperProfileFreshnessIT extends AbstractDatabaseIT {

  private static final UUID MAPPER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655443002");
  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655443004");

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private MapperProfileService mapperProfileService;
  @Autowired private CacheManager cacheManager;

  private ProfileFixtures profileFixtures;

  @BeforeEach
  void setUp() {
    profileFixtures = new ProfileFixtures(jdbcTemplate, mapperProfileService);
    profileFixtures.reset();
    clearMapperCache();
  }

  @AfterEach
  void tearDown() {
    clearMapperCache();
    profileFixtures.reset();
  }

  @Test
  void connectionsOwnMapperResolvesItsNewRevisionImmediately() throws Exception {
    profileFixtures.persistMapper(MAPPER_ID, "Base Mapper", true);
    profileFixtures.persistDataSource(SOURCE_ID, MAPPER_ID);
    mapperProfileService.findLatestForSource(SOURCE_ID);

    mapperProfileService.createNewVersion(disabledV2(MAPPER_ID), null);

    var connectionMapper =
        (UUID) profileFixtures.loadConnectionMapper(SOURCE_ID).get("ingress_mapper_id");
    var latest = mapperProfileService.getLatestMapperProfileFull(connectionMapper).profile();
    assertThat(latest.version()).isEqualTo(2);
    assertThat(latest.dynamicMapper().enabled()).isFalse();
    assertThat(mapperProfileService.findLatestForSource(SOURCE_ID).orElseThrow().version())
        .isEqualTo(1);
  }

  private void clearMapperCache() {
    var cache = cacheManager.getCache(MapperProfileCaches.BY_SOURCE_ID);
    if (cache != null) {
      cache.clear();
    }
  }

  private static MapperProfileFullMeta disabledV2(UUID mapperId) {
    var disabledDynamicMapper =
        new com.opencop.core.sdk.model.mapper.profile.DynamicMapper(
            UUID.randomUUID(), false, null, null, java.util.List.of());
    return new MapperProfileFullMeta(
        MapperProfile.builder()
            .withId(mapperId)
            .withName("Base Mapper")
            .withDescription("Test mapper")
            .withEditable(true)
            .withVersion(null)
            .withDynamicMapper(disabledDynamicMapper)
            .withArchived(false)
            .build(),
        "sample.xml",
        "sample".getBytes(StandardCharsets.UTF_8));
  }
}
