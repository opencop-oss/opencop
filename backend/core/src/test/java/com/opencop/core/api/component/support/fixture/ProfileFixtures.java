// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.support.fixture;

import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperProfileFullMeta;
import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import com.opencop.core.sdk.component.TestFixture;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.mapper.OnMissing;
import com.opencop.core.sdk.model.mapper.profile.DynamicMapperBuilder;
import com.opencop.core.sdk.model.mapper.rules.MappingRuleBuilder;
import com.opencop.core.sdk.model.mapper.rules.TargetField;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.testing.integration.NativeIntegrationFixture;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class ProfileFixtures implements TestFixture {

  /** The integration contributing every mapper {@link #persistPluginMapper} stores. */
  private static final UUID PLUGIN_INTEGRATION_ID =
      UUID.fromString("00000000-0000-4000-8000-0000000000ab");

  private final JdbcTemplate jdbcTemplate;
  private final MapperProfileService mapperProfileService;

  public ProfileFixtures(JdbcTemplate jdbcTemplate, MapperProfileService mapperProfileService) {
    this.jdbcTemplate = jdbcTemplate;
    this.mapperProfileService = mapperProfileService;
  }

  @Override
  public void reset() {
    jdbcTemplate.update("DELETE FROM profiles.data_source");
    jdbcTemplate.update("DELETE FROM profiles.data_mapper");
    jdbcTemplate.update("DELETE FROM profiles.mapper");
  }

  public Map<String, Object> loadMapperRow(UUID mapperId) {
    return jdbcTemplate.queryForMap(
        "SELECT * FROM profiles.data_mapper WHERE id = ? ORDER BY version DESC LIMIT 1", mapperId);
  }

  /** Loads one specific version row, for tests asserting per-version audit columns. */
  public Map<String, Object> loadMapperVersionRow(UUID mapperId, int version) {
    return jdbcTemplate.queryForMap(
        "SELECT * FROM profiles.data_mapper WHERE id = ? AND version = ?", mapperId, version);
  }

  public void setMapperArchived(UUID mapperId) {
    jdbcTemplate.update("UPDATE profiles.data_mapper SET archived = true WHERE id = ?", mapperId);
  }

  public void persistDataSource(UUID sourceId, UUID mapperId) {
    persistDataSource(sourceId, mapperId, AccessMode.READ);
  }

  public void persistDataSource(UUID sourceId, UUID mapperId, AccessMode access) {
    persistDataSource(sourceId, mapperId, TransportKind.TCP, access);
  }

  /** Persists a connection that decodes with the mapper when its access reads. */
  public void persistDataSource(
      UUID sourceId, UUID mapperId, TransportKind transportKind, AccessMode access) {
    NativeIntegrationFixture.seed(jdbcTemplate);
    jdbcTemplate.update(
        """
        INSERT INTO profiles.data_source
          (id, name, provider, access, ingress_mapper_id, egress_mapper_id, connection_spec,
           integration_id, connection_schema_version)
        VALUES (?, ?, ?::profiles.transport_kind, ?::profiles.access_mode, ?, ?,
                CAST('{}' AS jsonb), ?, ?)
        """,
        sourceId,
        "test-source-" + sourceId,
        transportKind.toString(),
        access.getAccess(),
        access.canRead() ? mapperId : null,
        TestEgressMapper.forAccess(jdbcTemplate, access),
        NativeIntegrationFixture.CONNECTION_SCHEMA.integrationId(),
        NativeIntegrationFixture.CONNECTION_SCHEMA.schemaVersion());
  }

  public Map<String, Object> loadConnectionMapper(UUID sourceId) {
    return jdbcTemplate.queryForMap(
        "SELECT ingress_mapper_id, updated_at FROM profiles.data_source WHERE id = ?", sourceId);
  }

  public UUID persistMapper(UUID mapperId, String name, boolean editable) {
    return persistMapper(mapperId, name, editable, null);
  }

  public UUID persistPluginMapper(UUID mapperId, String name) {
    return persistPluginMapper(mapperId, name, MapperQualifier.INGRESS);
  }

  /** A mapper the test plugin integration contributes, as startup registration records it. */
  public UUID persistPluginMapper(UUID mapperId, String name, MapperQualifier qualifier) {
    mapperProfileService.createMapperProfile(
        MapperProfile.builder()
            .withId(mapperId)
            .withName(name)
            .withDescription("Test plugin mapper")
            .withEditable(false)
            .withQualifier(qualifier)
            .withVersion(1)
            .withArchived(false)
            .build(),
        "sample".getBytes(StandardCharsets.UTF_8),
        "sample.xml",
        null);
    jdbcTemplate.update(
        """
        INSERT INTO config.integration (id, name, description)
        VALUES (?, 'Test plugin', 'Contributes test mappers')
        ON CONFLICT (id) DO NOTHING
        """,
        PLUGIN_INTEGRATION_ID);
    jdbcTemplate.update(
        "UPDATE profiles.mapper SET integration_id = ? WHERE id = ?",
        PLUGIN_INTEGRATION_ID,
        mapperId);
    return mapperId;
  }

  /**
   * Persists a mapper attributed to {@code createdBy}, for tests asserting on audit columns.
   *
   * <p>{@code created_by} carries a foreign key to {@code opencop_user}, so a non-null actor must
   * already be seeded (see {@code UserFixtures#seedUser}) or the insert fails.
   */
  public UUID persistMapper(UUID mapperId, String name, boolean editable, UUID createdBy) {
    return mapperProfileService.createMapperProfile(
        MapperProfile.builder()
            .withId(mapperId)
            .withName(name)
            .withDescription("Test mapper")
            .withEditable(editable)
            .withVersion(1)
            .withDynamicMapper(defaultMapper())
            .withArchived(false)
            .build(),
        "sample".getBytes(StandardCharsets.UTF_8),
        "sample.xml",
        createdBy);
  }

  public int mapperVersionCount(UUID mapperId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM profiles.data_mapper WHERE id = ?", Integer.class, mapperId);
  }

  public static MapperProfileFullMeta mapperFullMeta(UUID mapperId, String name, boolean editable) {
    return new MapperProfileFullMeta(
        MapperProfile.builder()
            .withId(mapperId)
            .withName(name)
            .withDescription("Test mapper")
            .withEditable(editable)
            .withVersion(null)
            .withDynamicMapper(defaultMapper())
            .withArchived(false)
            .build(),
        "sample.xml",
        "sample".getBytes(StandardCharsets.UTF_8));
  }

  private static com.opencop.core.sdk.model.mapper.profile.DynamicMapper defaultMapper() {
    return DynamicMapperBuilder.xml()
        .recordXPath("//Track")
        .addRule(
            MappingRuleBuilder.rule()
                .xpath("Lat/text()")
                .onMissing(OnMissing.ERROR)
                .target(TargetField.GEOMETRY_LAT)
                .build())
        .build();
  }
}
