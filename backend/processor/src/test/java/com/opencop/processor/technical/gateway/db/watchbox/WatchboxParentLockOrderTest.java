// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db.watchbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxEntity;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxJpaRepository;
import com.opencop.processor.business.model.watchbox.WatchboxStateKey;
import com.opencop.processor.business.model.watchbox.WatchboxStateLockResult;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** Proves Core's global bump waits for LOW before it can acquire HIGH from the same parent set. */
@SpringJUnitConfig(WatchboxParentLockOrderTest.TestConfig.class)
class WatchboxParentLockOrderTest {

  private static final UUID LOW_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
  private static final UUID HIGH_ID = UUID.fromString("00000000-0000-0000-0000-000000000202");
  private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-0000-0000-000000005011");
  private static final String CORE_SESSION = "watchbox-lock-order-core";
  private static final String PROCESSOR_SESSION = "watchbox-lock-order-processor";

  @Autowired private JdbcTemplate jdbc;
  @Autowired private WatchboxJpaRepository coreRepository;
  @Autowired private PostgresWatchboxTransitionStateRepository processorRepository;

  @Autowired
  @Qualifier("transactionManager")
  private PlatformTransactionManager jpaTransactionManager;

  @Autowired
  @Qualifier("jdbcTransactionManager")
  private PlatformTransactionManager jdbcTransactionManager;

  @BeforeEach
  void insertReversePhysicalOrder() {
    jdbc.update("DELETE FROM watchbox.watchbox");
    insertWatchbox(HIGH_ID, "high-first-physically");
    insertWatchbox(LOW_ID, "low-second-physically");
  }

  @Test
  void globalEpochBumpWaitsForLowestParentBeforeLockingHigherParent() throws Exception {
    var highLocked = new CountDownLatch(1);
    var releaseHigh = new CountDownLatch(1);
    var processorLockedLow = new CountDownLatch(1);
    var releaseProcessor = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(3)) {
      var coordinator =
          executor.submit(
              () ->
                  new TransactionTemplate(jdbcTransactionManager)
                      .executeWithoutResult(
                          _ -> {
                            jdbc.queryForObject(
                                "SELECT id FROM watchbox.watchbox WHERE id = ? FOR UPDATE",
                                UUID.class,
                                HIGH_ID);
                            highLocked.countDown();
                            await(releaseHigh, "release of the high-id parent");
                          }));
      await(highLocked, "coordinator high-id parent lock");

      var processor =
          executor.submit(
              () ->
                  Objects.requireNonNull(
                      new TransactionTemplate(jdbcTransactionManager)
                          .execute(
                              _ -> {
                                configureSession(PROCESSOR_SESSION);
                                var low =
                                    processorRepository.lockCurrentAndSelectForUpdate(
                                        List.of(key(LOW_ID, "low-object")));
                                processorLockedLow.countDown();
                                await(releaseProcessor, "Processor's second parent lock");
                                var high =
                                    processorRepository.lockCurrentAndSelectForUpdate(
                                        List.of(key(HIGH_ID, "high-object")));
                                return new ProcessorResults(low, high);
                              })));
      await(processorLockedLow, "Processor low-id parent lock");

      var core =
          executor.submit(
              () ->
                  Objects.requireNonNull(
                      new TransactionTemplate(jpaTransactionManager)
                          .execute(
                              _ -> {
                                configureSession(CORE_SESSION);
                                return coreRepository.bumpAllActiveConfigEpochs();
                              })));
      awaitLockWait(CORE_SESSION);

      // With the old heap-order UPDATE, Core now takes HIGH and waits for Processor's LOW. With
      // the ordered CTE, Core was already waiting for LOW and leaves HIGH free. Only the fixed
      // shape lets Processor acquire HIGH and commit without completing an A/B lock cycle.
      releaseHigh.countDown();
      coordinator.get(10, TimeUnit.SECONDS);
      awaitBlockedBy(CORE_SESSION, PROCESSOR_SESSION);
      releaseProcessor.countDown();

      var processorResults = processor.get(10, TimeUnit.SECONDS);
      assertThat(processorResults.low()).isInstanceOf(WatchboxStateLockResult.Current.class);
      assertThat(processorResults.high()).isInstanceOf(WatchboxStateLockResult.Current.class);
      assertThat(core.get(10, TimeUnit.SECONDS)).isEqualTo(2);
    } finally {
      releaseHigh.countDown();
      releaseProcessor.countDown();
    }

    assertThat(epoch(LOW_ID)).isEqualTo(2);
    assertThat(epoch(HIGH_ID)).isEqualTo(2);
  }

  private void configureSession(String applicationName) {
    jdbc.execute("SET LOCAL application_name = '" + applicationName + "'");
    jdbc.execute("SET LOCAL deadlock_timeout = '200ms'");
    jdbc.execute("SET LOCAL lock_timeout = '10s'");
    jdbc.execute("SET LOCAL enable_indexscan = off");
    jdbc.execute("SET LOCAL enable_indexonlyscan = off");
    jdbc.execute("SET LOCAL enable_bitmapscan = off");
  }

  private void awaitLockWait(String applicationName) {
    var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (System.nanoTime() < deadline) {
      var waiting =
          jdbc.queryForObject(
              """
              SELECT count(*)
              FROM pg_stat_activity
              WHERE application_name = ? AND wait_event_type = 'Lock'
              """,
              Long.class,
              applicationName);
      if (waiting != null && waiting == 1) {
        return;
      }
      try {
        Thread.sleep(25);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted awaiting " + applicationName, exception);
      }
    }
    throw new IllegalStateException(applicationName + " never reached its expected lock wait");
  }

  private void awaitBlockedBy(String blockedApplication, String blockerApplication) {
    var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (System.nanoTime() < deadline) {
      var blocked =
          jdbc.queryForObject(
              """
              SELECT count(*)
              FROM pg_stat_activity waiting
              JOIN pg_stat_activity blocker
                ON blocker.pid = ANY(pg_blocking_pids(waiting.pid))
              WHERE waiting.application_name = ? AND blocker.application_name = ?
              """,
              Long.class,
              blockedApplication,
              blockerApplication);
      if (blocked != null && blocked == 1) {
        return;
      }
      try {
        Thread.sleep(25);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(
            "Interrupted awaiting " + blockedApplication + " blocker", exception);
      }
    }
    throw new IllegalStateException(
        blockedApplication + " was never blocked by " + blockerApplication);
  }

  private void insertWatchbox(UUID id, String name) {
    jdbc.update(
        """
        INSERT INTO watchbox.watchbox (id, name, direction, polygon_json, filter_json)
        VALUES (?, ?, 'BOTH', '{"type":"Polygon","coordinates":[]}'::jsonb,
                '{"predicates":[]}'::jsonb)
        """,
        id,
        name);
  }

  private long epoch(UUID id) {
    return jdbc.queryForObject(
        "SELECT config_epoch FROM watchbox.watchbox WHERE id = ?", Long.class, id);
  }

  private static WatchboxStateKey key(UUID watchboxId, String objectId) {
    return new WatchboxStateKey(watchboxId, 1, SOURCE_ID, objectId);
  }

  private static void await(CountDownLatch latch, String description) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out awaiting " + description);
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted awaiting " + description, exception);
    }
  }

  private record ProcessorResults(WatchboxStateLockResult low, WatchboxStateLockResult high) {}

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories(basePackageClasses = WatchboxJpaRepository.class)
  static class TestConfig {

    @Bean(destroyMethod = "close")
    EmbeddedPostgres embeddedPostgres() throws Exception {
      return EmbeddedPostgres.builder().start();
    }

    @Bean
    DataSource dataSource(EmbeddedPostgres postgres) {
      return postgres.getPostgresDatabase();
    }

    @Bean
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
      return new JdbcTemplate(dataSource);
    }

    @Bean
    InitializingBean flywayInitializer(DataSource dataSource) {
      return () ->
          Flyway.configure()
              .dataSource(dataSource)
              .baselineOnMigrate(true)
              .locations("classpath:db/migration")
              .placeholders(FlywayGeoFeaturesPlaceholders.defaultPlaceholders())
              .load()
              .migrate();
    }

    @Bean
    @DependsOn("flywayInitializer")
    LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
      var factory = new LocalContainerEntityManagerFactoryBean();
      factory.setDataSource(dataSource);
      factory.setPackagesToScan(WatchboxEntity.class.getPackageName());
      factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
      factory.setJpaPropertyMap(
          Map.of(
              "hibernate.hbm2ddl.auto", "none",
              "hibernate.show_sql", "false"));
      return factory;
    }

    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
      return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean
    PlatformTransactionManager jdbcTransactionManager(DataSource dataSource) {
      return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    PostgresWatchboxTransitionStateRepository processorRepository(JdbcTemplate jdbc) {
      return new PostgresWatchboxTransitionStateRepository(jdbc);
    }
  }
}
