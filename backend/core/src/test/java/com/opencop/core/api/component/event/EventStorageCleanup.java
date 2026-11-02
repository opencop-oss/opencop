// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.event;

import com.opencop.core.api.component.support.fixture.UserFixtures;
import org.springframework.jdbc.core.JdbcTemplate;

final class EventStorageCleanup {

  private EventStorageCleanup() {}

  static void reset(JdbcTemplate jdbcTemplate) {
    jdbcTemplate.update("DELETE FROM geo.remarks");
    jdbcTemplate.update("DELETE FROM event_attachment");
    jdbcTemplate.update("DELETE FROM media_file");
    jdbcTemplate.update("DELETE FROM media_attachment");
    jdbcTemplate.update("DELETE FROM geo.features");
    UserFixtures.releaseUserReferences(jdbcTemplate);
    jdbcTemplate.update("DELETE FROM opencop_user");
  }
}
