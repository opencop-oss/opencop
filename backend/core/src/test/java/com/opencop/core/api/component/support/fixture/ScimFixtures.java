// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.support.fixture;

import com.opencop.core.sdk.component.TestFixture;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class ScimFixtures implements TestFixture {

  private final JdbcTemplate jdbcTemplate;

  public ScimFixtures(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void reset() {
    jdbcTemplate.update("DELETE FROM iam.user_group_member");
    jdbcTemplate.update("DELETE FROM iam.user_group");
    jdbcTemplate.update("DELETE FROM opencop_user_groups");
    jdbcTemplate.update("DELETE FROM opencop_user_preference");
    jdbcTemplate.update("DELETE FROM opencop_group");
    UserFixtures.releaseUserReferences(jdbcTemplate);
    jdbcTemplate.update("DELETE FROM opencop_user");
  }

  public int countUsers() {
    return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM opencop_user", Integer.class);
  }

  public int countGroups() {
    return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM iam.user_group", Integer.class);
  }

  public int countPreferencesForUser(UUID userId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM opencop_user_preference WHERE user_id = ?", Integer.class, userId);
  }

  public String preferenceLocale(UUID userId) {
    return jdbcTemplate.queryForObject(
        "SELECT locale FROM opencop_user_preference WHERE user_id = ?", String.class, userId);
  }

  public int countGroupMemberships(UUID groupId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM iam.user_group_member WHERE group_id = ?", Integer.class, groupId);
  }

  public int countGroupsByExternalId(String externalId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM iam.user_group WHERE idp_group_id = ?", Integer.class, externalId);
  }

  public boolean userActive(UUID userId) {
    return jdbcTemplate.queryForObject(
        "SELECT active FROM opencop_user WHERE id = ?", Boolean.class, userId);
  }

  public void setUserActive(UUID userId, boolean active) {
    jdbcTemplate.update("UPDATE opencop_user SET active = ? WHERE id = ?", active, userId);
  }

  public boolean userAdministrator(UUID userId) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "SELECT administrator FROM opencop_user WHERE id = ?", Boolean.class, userId));
  }

  public void setUserAdministrator(UUID userId, boolean administrator) {
    jdbcTemplate.update(
        "UPDATE opencop_user SET administrator = ? WHERE id = ?", administrator, userId);
  }

  public void setUserDeletedAt(UUID userId, Instant deletedAt) {
    jdbcTemplate.update(
        "UPDATE opencop_user SET deleted_at = ? WHERE id = ?", Timestamp.from(deletedAt), userId);
  }

  public boolean userHasDeletionTime(UUID userId) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "SELECT deleted_at IS NOT NULL FROM opencop_user WHERE id = ?", Boolean.class, userId));
  }

  public long userAccountAccessRevision(UUID userId) {
    return jdbcTemplate.queryForObject(
        "SELECT account_access_revision FROM opencop_user WHERE id = ?", Long.class, userId);
  }

  public void setUserExternalId(UUID userId, String externalId) {
    jdbcTemplate.update("UPDATE opencop_user SET idp_user_id = ? WHERE id = ?", externalId, userId);
  }

  public UUID insertGroup(String name, String idpIssuer, String idpGroupId) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO iam.user_group (id, name, idp_issuer, idp_group_id) VALUES (?, ?, ?, ?)",
        id,
        name,
        idpIssuer,
        idpGroupId);
    return id;
  }

  public boolean groupExists(UUID groupId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM iam.user_group WHERE id = ?", Integer.class, groupId);
    return count != null && count > 0;
  }

  public String groupName(UUID groupId) {
    return jdbcTemplate.queryForObject(
        "SELECT name FROM iam.user_group WHERE id = ?", String.class, groupId);
  }

  public UUID insertUser(String idpIssuer, String idpUserId, String username) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO opencop_user (
          id, idp_user_id, idp_username, idp_issuer, username, email, first_name, last_name, display_name
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        id,
        idpUserId,
        username,
        idpIssuer,
        username,
        username + "@example.test",
        "Test",
        "User",
        "Test User");
    return id;
  }
}
