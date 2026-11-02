// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.component.support;

import com.opencop.api.connect.ports.gateway.repository.DesiredSocketSourceRepository;
import com.opencop.api.connect.technical.gateway.repository.DesiredSocketSourceJpaRepository;
import com.opencop.api.connect.technical.gateway.repository.JpaDesiredSocketSourceRepository;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import com.opencop.core.sdk.component.database.AbstractDatabaseIT;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import com.opencop.core.sdk.testing.integration.NativeIntegrationFixture;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import tools.jackson.databind.ObjectMapper;

public class ConnectComponentDatabaseSupport extends AbstractDatabaseIT {

  private static final EmbeddedPostgres POSTGRES = embeddedPostgres();
  private static final javax.sql.DataSource DATA_SOURCE = POSTGRES.getPostgresDatabase();
  private static final NamedParameterJdbcTemplate JDBC =
      new NamedParameterJdbcTemplate(DATA_SOURCE);

  static {
    Flyway.configure()
        .dataSource(DATA_SOURCE)
        .baselineOnMigrate(true)
        .locations("classpath:db/migration")
        .placeholders(FlywayGeoFeaturesPlaceholders.defaultPlaceholders())
        .load()
        .migrate();
  }

  private static final EntityManagerFactory ENTITY_MANAGER_FACTORY = entityManagerFactory();

  // Connect never decodes or encodes payloads, so one shared ingress and one shared egress mapper
  // identity serve every test source whose access needs them.
  private static final UUID TEST_INGRESS_MAPPER_ID =
      UUID.fromString("3c9d1e44-6a2b-4f0e-8b7d-1a2b3c4d5e6f");
  private static final UUID TEST_EGRESS_MAPPER_ID =
      UUID.fromString("7b1f0c55-4f7e-4a7a-9d8e-2f4e5a6b7c80");

  public static void resetSources() {
    JDBC.getJdbcTemplate().execute("TRUNCATE TABLE profiles.data_source CASCADE");
  }

  public static void seedSocketSource(
      UUID sourceId,
      String name,
      ConnectionSpec spec,
      boolean enabled,
      String assignedInstance,
      ObjectMapper objectMapper) {
    seedSocketSource(
        sourceId, name, spec, AccessMode.READ, enabled, assignedInstance, objectMapper);
  }

  public static void seedSocketSource(
      UUID sourceId,
      String name,
      ConnectionSpec spec,
      AccessMode access,
      boolean enabled,
      String assignedInstance,
      ObjectMapper objectMapper) {
    var provider = spec.transport().kind();
    NativeIntegrationFixture.seed(JDBC.getJdbcOperations());
    JDBC.update(
        """
        INSERT INTO profiles.data_source(
          id, name, tenant, provider, access, ingress_mapper_id, egress_mapper_id,
          connection_spec, enabled, assigned_instance, integration_id, connection_schema_version
        )
        VALUES(
          :id, :name, 'default', :provider::profiles.transport_kind, :access::profiles.access_mode,
          CAST(:ingressMapperId AS uuid), CAST(:egressMapperId AS uuid),
          CAST(:connectionSpec AS jsonb), :enabled, :assignedInstance, :integrationId,
          :schemaVersion
        )
        ON CONFLICT (id) DO UPDATE SET
          name = EXCLUDED.name,
          provider = EXCLUDED.provider,
          access = EXCLUDED.access,
          ingress_mapper_id = EXCLUDED.ingress_mapper_id,
          egress_mapper_id = EXCLUDED.egress_mapper_id,
          connection_spec = EXCLUDED.connection_spec,
          enabled = EXCLUDED.enabled,
          assigned_instance = EXCLUDED.assigned_instance,
          updated_at = now()
        """,
        new MapSqlParameterSource()
            .addValue("id", sourceId)
            .addValue("name", name)
            .addValue("provider", provider.name())
            .addValue("access", access.getAccess())
            .addValue("ingressMapperId", ingressMapperIdFor(access))
            .addValue("egressMapperId", egressMapperIdFor(access))
            .addValue("connectionSpec", objectMapper.writeValueAsString(spec))
            .addValue("enabled", enabled)
            .addValue("assignedInstance", assignedInstance)
            .addValue("integrationId", NativeIntegrationFixture.CONNECTION_SCHEMA.integrationId())
            .addValue("schemaVersion", NativeIntegrationFixture.CONNECTION_SCHEMA.schemaVersion()));
  }

  public static void setSocketSourceEnabled(UUID sourceId, boolean enabled) {
    JDBC.update(
        """
        UPDATE profiles.data_source
           SET enabled = :enabled, updated_at = now()
         WHERE id = :id
        """,
        Map.of("id", sourceId, "enabled", enabled));
  }

  /** Changes the source's access together with the test mappers that access requires. */
  public static void setSocketSourceAccess(UUID sourceId, AccessMode access) {
    JDBC.update(
        """
        UPDATE profiles.data_source
           SET access = :access::profiles.access_mode,
               ingress_mapper_id = CAST(:ingressMapperId AS uuid),
               egress_mapper_id = CAST(:egressMapperId AS uuid),
               updated_at = now()
         WHERE id = :id
        """,
        new MapSqlParameterSource()
            .addValue("id", sourceId)
            .addValue("access", access.getAccess())
            .addValue("ingressMapperId", ingressMapperIdFor(access))
            .addValue("egressMapperId", egressMapperIdFor(access)));
  }

  private static UUID ingressMapperIdFor(AccessMode access) {
    return access.canRead()
        ? seedMapperIdentity(TEST_INGRESS_MAPPER_ID, MapperQualifier.INGRESS)
        : null;
  }

  private static UUID egressMapperIdFor(AccessMode access) {
    return access.canWrite()
        ? seedMapperIdentity(TEST_EGRESS_MAPPER_ID, MapperQualifier.EGRESS)
        : null;
  }

  private static UUID seedMapperIdentity(UUID mapperId, MapperQualifier qualifier) {
    JDBC.update(
        """
        INSERT INTO profiles.mapper (id, qualifier)
        VALUES (:id, CAST(:qualifier AS profiles.mapper_qualifier))
        ON CONFLICT (id) DO NOTHING
        """,
        Map.of("id", mapperId, "qualifier", qualifier.name()));
    return mapperId;
  }

  public static void setSocketSourceAssignedInstance(UUID sourceId, String assignedInstance) {
    JDBC.update(
        """
        UPDATE profiles.data_source
           SET assigned_instance = :assignedInstance, updated_at = now()
         WHERE id = :id
        """,
        new MapSqlParameterSource()
            .addValue("id", sourceId)
            .addValue("assignedInstance", assignedInstance));
  }

  public static void setSocketSourceIntegration(
      UUID sourceId, IntegrationConnectionSchemaReference integration) {
    JDBC.update(
        """
        INSERT INTO config.integration (id, name, description)
        VALUES (:id, 'Connect test integration', 'Connect component-test row')
        ON CONFLICT (id) DO NOTHING
        """,
        Map.of("id", integration.integrationId()));
    JDBC.update(
        """
        INSERT INTO config.integration_connection_schema (integration_id, schema_version)
        VALUES (:id, :schemaVersion)
        ON CONFLICT (integration_id, schema_version) DO NOTHING
        """,
        Map.of("id", integration.integrationId(), "schemaVersion", integration.schemaVersion()));
    JDBC.update(
        """
        UPDATE profiles.data_source
           SET integration_id = :id,
               connection_schema_version = :schemaVersion,
               updated_at = now()
         WHERE id = :sourceId
        """,
        Map.of(
            "sourceId",
            sourceId,
            "id",
            integration.integrationId(),
            "schemaVersion",
            integration.schemaVersion()));
  }

  public static void archiveSocketSource(UUID sourceId, UUID archivedBy) {
    seedUser(archivedBy);
    JDBC.update(
        """
        UPDATE profiles.data_source
           SET archived_at = now(),
               archived_by = :archivedBy,
               updated_at = now()
         WHERE id = :id
        """,
        Map.of("id", sourceId, "archivedBy", archivedBy));
  }

  private static void seedUser(UUID userId) {
    JDBC.update(
        """
        INSERT INTO opencop_user (
          id, idp_user_id, idp_username, idp_issuer, username, email, first_name, last_name, display_name
        ) VALUES (:id, :idpUserId, :username, :issuer, :username, :email, :firstName, :lastName, :displayName)
        ON CONFLICT (id) DO NOTHING
        """,
        new MapSqlParameterSource()
            .addValue("id", userId)
            .addValue("idpUserId", "idp-" + userId)
            .addValue("username", "user-" + userId)
            .addValue("issuer", "https://issuer.test")
            .addValue("email", "user@example.test")
            .addValue("firstName", "Test")
            .addValue("lastName", "User")
            .addValue("displayName", "Test User"));
  }

  public static void deleteSocketSource(UUID sourceId) {
    JDBC.update("DELETE FROM profiles.data_source WHERE id = :id", Map.of("id", sourceId));
  }

  public static DesiredSocketSourceRepository desiredSocketSourceRepository(
      ObjectMapper objectMapper) {
    return new JpaDesiredSocketSourceRepository(desiredSocketSourceJpaRepository(), objectMapper);
  }

  private static EmbeddedPostgres embeddedPostgres() {
    try {
      var field = AbstractDatabaseIT.class.getDeclaredField("POSTGRES");
      field.setAccessible(true);
      return (EmbeddedPostgres) field.get(null);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to reuse embedded PostgreSQL", e);
    }
  }

  private static DesiredSocketSourceJpaRepository desiredSocketSourceJpaRepository() {
    var entityManager =
        SharedEntityManagerCreator.createSharedEntityManager(ENTITY_MANAGER_FACTORY);
    return new JpaRepositoryFactory(entityManager)
        .getRepository(DesiredSocketSourceJpaRepository.class);
  }

  /** Stores integration settings overrides for a source already bound to its integration. */
  public static void setSocketSourceSettings(
      UUID sourceId, IntegrationConnectionSchemaReference integration, String settingsJson) {
    JDBC.update(
        """
        INSERT INTO profiles.connection_integration_settings
          (source_id, integration_id, connection_schema_version, settings)
        VALUES (:sourceId, :integrationId, :schemaVersion, CAST(:settings AS jsonb))
        """,
        Map.of(
            "sourceId",
            sourceId,
            "integrationId",
            integration.integrationId(),
            "schemaVersion",
            integration.schemaVersion(),
            "settings",
            settingsJson));
  }

  private static EntityManagerFactory entityManagerFactory() {
    var factory = new LocalContainerEntityManagerFactoryBean();
    factory.setDataSource(DATA_SOURCE);
    factory.setPackagesToScan("com.opencop.api.connect.technical.gateway.repository");
    factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
    factory.setPersistenceUnitName("connect-component-test");
    factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
    factory.afterPropertiesSet();
    var entityManagerFactory = factory.getObject();
    if (entityManagerFactory == null) {
      throw new IllegalStateException("Failed to create component test EntityManagerFactory");
    }
    return entityManagerFactory;
  }
}
