// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.iam;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import com.opencop.core.sdk.testing.integration.NativeIntegrationFixture;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;

@SpringJUnitConfig(EffectiveSourceRoleRepositoryTest.Config.class)
class EffectiveSourceRoleRepositoryTest {

  private final EffectiveSourceRoleRepository repository;
  private final NamedParameterJdbcTemplate jdbc;

  @Autowired
  EffectiveSourceRoleRepositoryTest(
      EffectiveSourceRoleRepository repository, NamedParameterJdbcTemplate jdbc) {
    this.repository = repository;
    this.jdbc = jdbc;
  }

  @Test
  void findActiveEffectiveRole_mapsFunctionProjectionForSingleActiveSource() {
    var userId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    insertUser(userId);
    insertSource(sourceId);
    insertServerUserAccess(sourceId, userId, "ADMIN");

    var role = repository.findActiveEffectiveRole(userId, sourceId);

    assertThat(role).isPresent();
    assertThat(role.orElseThrow().getSourceId()).isEqualTo(sourceId);
    assertThat(role.orElseThrow().getRole()).isEqualTo("ADMIN");
    assertThat(role.orElseThrow().getVia()).isEqualTo("DIRECT");
  }

  @Test
  void findEffectiveRolesIncludingArchivedSources_mapsArchivedListingRows() {
    var userId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    var archivedBy = UUID.randomUUID();
    insertUser(userId);
    insertUser(archivedBy);
    insertSource(sourceId);
    insertServerUserAccess(sourceId, userId, "OWNER");
    archiveSource(sourceId, archivedBy);

    assertThat(repository.findActiveEffectiveRole(userId, sourceId)).isEmpty();
    assertThat(repository.findEffectiveRolesIncludingArchivedSources(userId))
        .singleElement()
        .satisfies(
            role -> {
              assertThat(role.getSourceId()).isEqualTo(sourceId);
              assertThat(role.getRole()).isEqualTo("OWNER");
              assertThat(role.getVia()).isEqualTo("DIRECT");
            });
  }

  private void insertServerUserAccess(UUID sourceId, UUID userId, String role) {
    jdbc.update(
        """
        INSERT INTO iam.server_user_access(server_id, user_id, role)
        VALUES (:sourceId, :userId, CAST(:role AS iam.server_role))
        """,
        Map.of("sourceId", sourceId, "userId", userId, "role", role));
  }

  private void insertSource(UUID sourceId) {
    var mapperId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO profiles.mapper(id, qualifier)
        VALUES (:id, 'INGRESS'::profiles.mapper_qualifier)
        ON CONFLICT (id) DO NOTHING
        """,
        Map.of("id", mapperId));
    jdbc.update(
        """
        INSERT INTO profiles.data_mapper(id, version, name, mapper)
        VALUES (:id, 1, :name, '{}'::jsonb)
        """,
        Map.of("id", mapperId, "name", "mapper-" + mapperId));
    NativeIntegrationFixture.seed(jdbc.getJdbcOperations());
    jdbc.update(
        """
        INSERT INTO profiles.data_source(
          id, name, tenant, provider, access, ingress_mapper_id,
          connection_spec, integration_id, connection_schema_version
        )
        VALUES(
          :id, :name, 'default', 'TCP'::profiles.transport_kind, 'R'::profiles.access_mode,
          :mapperId, '{}'::jsonb,
          :integrationId, :schemaVersion
        )
        """,
        Map.of(
            "id",
            sourceId,
            "name",
            "source-" + sourceId,
            "mapperId",
            mapperId,
            "integrationId",
            NativeIntegrationFixture.CONNECTION_SCHEMA.integrationId(),
            "schemaVersion",
            NativeIntegrationFixture.CONNECTION_SCHEMA.schemaVersion()));
  }

  private void archiveSource(UUID sourceId, UUID archivedBy) {
    jdbc.update(
        """
        UPDATE profiles.data_source
        SET archived_at = now(),
            archived_by = :archivedBy
        WHERE id = :id
        """,
        Map.of("archivedBy", archivedBy, "id", sourceId));
  }

  private void insertUser(UUID userId) {
    jdbc.update(
        """
        INSERT INTO opencop_user (
          id, idp_user_id, idp_username, idp_issuer, username, email, first_name, last_name, display_name
        ) VALUES (:id, :idpUserId, :username, :issuer, :username, :email, :firstName, :lastName, :displayName)
        """,
        Map.of(
            "id",
            userId,
            "idpUserId",
            "idp-" + userId,
            "username",
            "user-" + userId,
            "issuer",
            "https://issuer.test",
            "email",
            "user@example.test",
            "firstName",
            "Test",
            "lastName",
            "User",
            "displayName",
            "Test User"));
  }

  @EnableJpaRepositories(basePackageClasses = EffectiveSourceRoleRepository.class)
  static class Config {

    @Bean(destroyMethod = "close")
    EmbeddedPostgres embeddedPostgres() throws IOException {
      return EmbeddedPostgres.builder().start();
    }

    @Bean
    DataSource dataSource(EmbeddedPostgres postgres) {
      return postgres.getPostgresDatabase();
    }

    @Bean
    NamedParameterJdbcTemplate jdbcTemplate(DataSource dataSource) {
      return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    Flyway flyway(DataSource dataSource) {
      return Flyway.configure()
          .dataSource(dataSource)
          .baselineOnMigrate(true)
          .locations("classpath:db/migration")
          .placeholders(FlywayGeoFeaturesPlaceholders.defaultPlaceholders())
          .load();
    }

    @Bean
    InitializingBean flywayInitializer(Flyway flyway) {
      return flyway::migrate;
    }

    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(
        DataSource dataSource, InitializingBean flywayInitializer) {
      var factory = new LocalContainerEntityManagerFactoryBean();
      factory.setDataSource(dataSource);
      factory.setPackagesToScan("com.opencop.core.sdk.technical.gateway.db.iam");
      factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
      factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
      return factory;
    }

    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
      return new JpaTransactionManager(entityManagerFactory);
    }
  }
}
