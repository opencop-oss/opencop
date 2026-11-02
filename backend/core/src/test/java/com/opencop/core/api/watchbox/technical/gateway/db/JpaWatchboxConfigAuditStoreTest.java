// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.technical.gateway.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.api.watchbox.business.model.WatchboxConfig;
import com.opencop.core.api.watchbox.ports.usecase.WatchboxCaller;
import com.opencop.core.sdk.model.watchbox.WatchboxDirection;
import com.opencop.core.sdk.model.watchbox.WatchboxFilter;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxConfigAuditJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.geojson.LngLatAlt;
import org.geojson.Polygon;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

class JpaWatchboxConfigAuditStoreTest {

  private static final String SENSITIVE_DETAIL =
      "Failing row contains (private description, actor@example.test)";
  private static final UUID WATCHBOX_ID = UUID.fromString("730e8400-e29b-41d4-a716-446655441001");
  private static final UUID ACTOR_ID = UUID.fromString("730e8400-e29b-41d4-a716-446655441002");
  private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");

  private final Logger logger = (Logger) LoggerFactory.getLogger(JpaWatchboxConfigAuditStore.class);
  private Level originalLevel;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void setUpAppender() {
    originalLevel = logger.getLevel();
    logger.setLevel(Level.ERROR);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDownAppender() {
    logger.detachAppender(appender);
    appender.stop();
    logger.setLevel(originalLevel);
  }

  @Test
  void recordCreate_whenDatabaseWriteFails_logsSafeContextAndRethrowsUnchanged() {
    var repository = mock(WatchboxConfigAuditJpaRepository.class);
    var failure = new IllegalStateException(SENSITIVE_DETAIL);
    when(repository.saveAndFlush(any())).thenThrow(failure);
    var store =
        new JpaWatchboxConfigAuditStore(
            repository,
            JsonMapper.builder().findAndAddModules().build(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    assertThatThrownBy(
            () ->
                store.recordCreate(
                    config(),
                    new WatchboxCaller(ACTOR_ID, true, "Audit Actor", "actor@example.test")))
        .isSameAs(failure);

    assertThat(appender.list).hasSize(1);
    var event = appender.list.getFirst();
    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
    assertThat(event.getFormattedMessage())
        .contains(
            "WATCHBOX_CONFIG_AUDIT_WRITE_FAILED",
            "operation=CREATE",
            "watchbox_id=" + WATCHBOX_ID,
            "exception_class=IllegalStateException")
        .doesNotContain(SENSITIVE_DETAIL, "private description", "actor@example.test");
    assertThat(event.getThrowableProxy()).isNull();
  }

  private static WatchboxConfig config() {
    return new WatchboxConfig(
        WATCHBOX_ID,
        "safe audit fixture",
        null,
        false,
        WatchboxDirection.BOTH,
        square(),
        WatchboxFilter.empty(),
        Set.of(),
        1,
        NOW,
        NOW);
  }

  private static Polygon square() {
    return new Polygon(
        List.of(
            new LngLatAlt(-100.2, 39.8),
            new LngLatAlt(-99.8, 39.8),
            new LngLatAlt(-99.8, 40.2),
            new LngLatAlt(-100.2, 40.2),
            new LngLatAlt(-100.2, 39.8)));
  }
}
