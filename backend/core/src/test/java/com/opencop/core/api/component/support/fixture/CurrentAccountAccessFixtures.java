// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.support.fixture;

import com.opencop.core.sdk.component.TestFixture;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class CurrentAccountAccessFixtures implements TestFixture {

  private final JdbcTemplate jdbcTemplate;

  public CurrentAccountAccessFixtures(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void reset() {
    jdbcTemplate.update("DELETE FROM opencop_user_preference");
    UserFixtures.releaseUserReferences(jdbcTemplate);
    jdbcTemplate.update("DELETE FROM opencop_user");
  }

  public void seedUser(UUID userId, boolean active, boolean administrator) {
    jdbcTemplate.update(
        """
        INSERT INTO opencop_user (
          id, idp_user_id, idp_username, idp_issuer, username, active, administrator
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        userId,
        "idp-" + userId,
        "user-" + userId,
        "https://issuer.test",
        "user-" + userId,
        active,
        administrator);
  }

  public void setAdministrator(UUID userId, boolean administrator) {
    jdbcTemplate.update(
        "UPDATE opencop_user SET administrator = ? WHERE id = ?", administrator, userId);
  }

  public void setActive(UUID userId, boolean active) {
    jdbcTemplate.update("UPDATE opencop_user SET active = ? WHERE id = ?", active, userId);
  }

  public void setSessionGeneration(UUID userId, long sessionGeneration) {
    jdbcTemplate.update(
        "UPDATE opencop_user SET session_generation = ? WHERE id = ?", sessionGeneration, userId);
  }

  public boolean administrator(UUID userId) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "SELECT administrator FROM opencop_user WHERE id = ?", Boolean.class, userId));
  }

  public boolean active(UUID userId) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "SELECT active FROM opencop_user WHERE id = ?", Boolean.class, userId));
  }

  public String displayName(UUID userId) {
    return jdbcTemplate.queryForObject(
        "SELECT display_name FROM opencop_user WHERE id = ?", String.class, userId);
  }

  public long accountAccessRevision(UUID userId) {
    return jdbcTemplate.queryForObject(
        "SELECT account_access_revision FROM opencop_user WHERE id = ?", Long.class, userId);
  }

  public long sessionGeneration(UUID userId) {
    return jdbcTemplate.queryForObject(
        "SELECT session_generation FROM opencop_user WHERE id = ?", Long.class, userId);
  }
}
