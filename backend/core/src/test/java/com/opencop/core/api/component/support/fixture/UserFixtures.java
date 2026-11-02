// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.support.fixture;

import com.opencop.core.api.general.business.model.user.UserPreference;
import com.opencop.core.api.general.business.model.user.UserPreferencePatch;
import com.opencop.core.api.general.ports.gateway.repository.UserPreferenceRepository;
import com.opencop.core.sdk.business.model.MeasurementSystem;
import com.opencop.core.sdk.business.model.TemperatureUnit;
import com.opencop.core.sdk.component.TestFixture;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class UserFixtures implements TestFixture {

  private final JdbcTemplate jdbcTemplate;
  private final UserPreferenceRepository userPreferenceRepository;

  public UserFixtures(
      JdbcTemplate jdbcTemplate, UserPreferenceRepository userPreferenceRepository) {
    this.jdbcTemplate = jdbcTemplate;
    this.userPreferenceRepository = userPreferenceRepository;
  }

  @Override
  public void reset() {
    jdbcTemplate.update("DELETE FROM opencop_user_preference");
    releaseUserReferences(jdbcTemplate);
    jdbcTemplate.update("DELETE FROM opencop_user");
  }

  /**
   * Clears every reference to {@code opencop_user} so the table can be emptied. Suites share one
   * database, so a suite that wipes users has to release rows an earlier suite left behind.
   *
   * <p>Archived layers and sources are deleted because {@code archived_by} is only set alongside
   * the archive state. The audit actors are nulled rather than deleted: they are set on active rows
   * too, and a suite that seeded a layer or source still expects it to exist. The foreign keys
   * themselves stay intact — this releases the references, it does not weaken the schema.
   */
  public static void releaseUserReferences(JdbcTemplate jdbcTemplate) {
    jdbcTemplate.update("DELETE FROM iam.layer WHERE archived_at IS NOT NULL");
    jdbcTemplate.update("DELETE FROM profiles.data_source WHERE archived_at IS NOT NULL");
    jdbcTemplate.update("UPDATE iam.layer SET created_by = NULL, updated_by = NULL");
    jdbcTemplate.update("UPDATE profiles.data_source SET created_by = NULL, updated_by = NULL");
    jdbcTemplate.update("UPDATE profiles.data_mapper SET created_by = NULL, updated_by = NULL");
  }

  public void seedUser(UUID userId) {
    seedUser(jdbcTemplate, userId);
  }

  /**
   * Seeds a user row, for suites that need an actor satisfying an {@code opencop_user} foreign key
   * without pulling the rest of this fixture's dependencies into their test slice.
   */
  public static void seedUser(JdbcTemplate jdbcTemplate, UUID userId) {
    jdbcTemplate.update(
        """
        INSERT INTO opencop_user (
          id, idp_user_id, idp_username, idp_issuer, username, email, first_name, last_name, display_name
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO NOTHING
        """,
        userId,
        "idp-" + userId,
        "user-" + userId,
        "https://issuer.test",
        "user-" + userId,
        "user@example.com",
        "Test",
        "User",
        "Test User");
  }

  public void setUsername(UUID userId, String username) {
    jdbcTemplate.update("UPDATE opencop_user SET username = ? WHERE id = ?", username, userId);
  }

  public void setAdministrator(UUID userId, boolean administrator) {
    jdbcTemplate.update(
        "UPDATE opencop_user SET administrator = ? WHERE id = ?", administrator, userId);
  }

  public boolean administrator(UUID userId) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "SELECT administrator FROM opencop_user WHERE id = ?", Boolean.class, userId));
  }

  public long accountAccessRevision(UUID userId) {
    return jdbcTemplate.queryForObject(
        "SELECT account_access_revision FROM opencop_user WHERE id = ?", Long.class, userId);
  }

  public UserPreference persistPreference(
      UUID userId,
      String locale,
      MeasurementSystem measurementSystem,
      TemperatureUnit temperatureUnit)
      throws Exception {
    seedUser(userId);
    return userPreferenceRepository.patch(
        userId,
        new UserPreferencePatch(
            Optional.empty(),
            Optional.of(locale),
            Optional.of(measurementSystem),
            Optional.of(temperatureUnit),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            null));
  }

  public int countPreferences() {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM opencop_user_preference", Integer.class);
  }

  public Map<String, Object> loadPreferenceRow(UUID userId) {
    return jdbcTemplate.queryForMap(
        "SELECT * FROM opencop_user_preference WHERE user_id = ?", userId);
  }
}
