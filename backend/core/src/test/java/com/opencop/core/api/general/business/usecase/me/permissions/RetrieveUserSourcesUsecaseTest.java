// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.me.permissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.api.general.business.service.DataSourceRuntimeService;
import com.opencop.core.api.iam.ports.usecase.access.me.UserSpecificManager;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.DataSourceRuntime;
import com.opencop.core.sdk.model.datasource.RuntimeStatus;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.model.iam.source.AccessVia;
import com.opencop.core.sdk.model.iam.source.SourceRole;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class RetrieveUserSourcesUsecaseTest {

  private static final UUID USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID VISIBLE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
  private static final UUID FILTERED_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

  /**
   * A role whose source the visibility filter excluded is an ordinary outcome, not a fault. It used
   * to log a warning per occurrence, so a caller holding roles on many archived sources paid one
   * line per archived source on every permissions read.
   */
  @Test
  void get_whenVisibilityFiltersOutASource_omitsItWithoutWarning() {
    var fixture = Fixture.create();
    fixture.withRoles(
        new SourceRole(VISIBLE_ID, ServerRole.OWNER, AccessVia.DIRECT),
        new SourceRole(FILTERED_ID, ServerRole.VIEWER, AccessVia.DIRECT));
    fixture.withVisibleRuntime(VISIBLE_ID);

    var sources = fixture.usecase().get(USER_ID, RecordVisibility.ACTIVE);

    assertEquals(1, sources.size());
    assertEquals(VISIBLE_ID, sources.getFirst().serverId());
    assertThat(fixture.warnings()).isEmpty();
  }

  /**
   * The same filter runs in the other direction under ARCHIVED visibility, where the excluded
   * source is an active one. Both directions must stay quiet.
   */
  @Test
  void get_whenArchivedVisibilityFiltersOutAnActiveSource_omitsItWithoutWarning() {
    var fixture = Fixture.create();
    fixture.withRoles(
        new SourceRole(VISIBLE_ID, ServerRole.OWNER, AccessVia.DIRECT),
        new SourceRole(FILTERED_ID, ServerRole.VIEWER, AccessVia.DIRECT));
    fixture.withVisibleRuntime(FILTERED_ID);

    var sources = fixture.usecase().get(USER_ID, RecordVisibility.ARCHIVED);

    assertEquals(1, sources.size());
    assertEquals(FILTERED_ID, sources.getFirst().serverId());
    assertThat(fixture.warnings()).isEmpty();
  }

  @Test
  void get_whenEveryRoleIsFilteredOut_returnsEmptyAndStaysQuiet() {
    var fixture = Fixture.create();
    fixture.withRoles(
        new SourceRole(VISIBLE_ID, ServerRole.OWNER, AccessVia.DIRECT),
        new SourceRole(FILTERED_ID, ServerRole.VIEWER, AccessVia.DIRECT));
    fixture.withVisibleRuntime();

    var sources = fixture.usecase().get(USER_ID, RecordVisibility.ACTIVE);

    assertThat(sources).isEmpty();
    assertThat(fixture.warnings()).isEmpty();
  }

  /** The direct grant is reported beside the effective role for sources that survive the filter. */
  @Test
  void get_whenAccessIsDirect_reportsTheDirectRoleBesideTheEffectiveRole() {
    var fixture = Fixture.create();
    fixture.withRoles(new SourceRole(VISIBLE_ID, ServerRole.OWNER, AccessVia.BOTH));
    fixture.withDirectRoles(new SourceRole(VISIBLE_ID, ServerRole.VIEWER, AccessVia.DIRECT));
    fixture.withVisibleRuntime(VISIBLE_ID);

    var sources = fixture.usecase().get(USER_ID, RecordVisibility.ACTIVE);

    assertEquals(1, sources.size());
    assertEquals(ServerRole.OWNER, sources.getFirst().role());
    assertEquals(ServerRole.VIEWER, sources.getFirst().directRole());
  }

  /** Layer-derived access carries no direct grant, so directRole stays null. */
  @Test
  void get_whenAccessIsLayerDerived_leavesTheDirectRoleNull() {
    var fixture = Fixture.create();
    fixture.withRoles(new SourceRole(VISIBLE_ID, ServerRole.OWNER, AccessVia.INDIRECT));
    fixture.withVisibleRuntime(VISIBLE_ID);

    var sources = fixture.usecase().get(USER_ID, RecordVisibility.ACTIVE);

    assertEquals(1, sources.size());
    assertEquals(ServerRole.OWNER, sources.getFirst().role());
    assertThat(sources.getFirst().directRole()).isNull();
  }

  private record Fixture(
      UserSpecificManager userSpecificManager,
      DataSourceRuntimeService runtimeService,
      ListAppender<ILoggingEvent> appender) {

    static Fixture create() {
      var appender = new ListAppender<ILoggingEvent>();
      appender.start();
      var logger = (Logger) LoggerFactory.getLogger(RetrieveUserSourcesUsecase.class);
      logger.detachAppender(appender);
      logger.addAppender(appender);
      var fixture =
          new Fixture(
              mock(UserSpecificManager.class), mock(DataSourceRuntimeService.class), appender);
      fixture.withDirectRoles();
      return fixture;
    }

    void withRoles(SourceRole... roles) {
      when(userSpecificManager.getUserServers(USER_ID)).thenReturn(List.of(roles));
    }

    void withDirectRoles(SourceRole... roles) {
      when(userSpecificManager.getUserDirectServers(USER_ID)).thenReturn(List.of(roles));
    }

    /** Only these ids survive the visibility filter the runtime read applies. */
    void withVisibleRuntime(UUID... ids) {
      var runtimes = Arrays.stream(ids).collect(Collectors.toMap(id -> id, Fixture::runtimeFor));
      when(runtimeService.getWithRuntime(anySet(), any(RecordVisibility.class)))
          .thenReturn(runtimes);
    }

    List<ILoggingEvent> warnings() {
      return appender.list.stream()
          .filter(event -> event.getLevel().isGreaterOrEqual(Level.WARN))
          .toList();
    }

    RetrieveUserSourcesUsecase usecase() {
      return new RetrieveUserSourcesUsecase(userSpecificManager, runtimeService);
    }

    private static DataSourceRuntime runtimeFor(UUID id) {
      var dataSource =
          new SocketDataSource(
              id,
              "source-" + id,
              "default",
              new ConnectionSpec(
                  new TcpConfig("127.0.0.1", 8087, null, null, null, null, new NoSecurity())),
              null,
              null,
              null,
              AccessMode.READ,
              true,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              true);
      return new DataSourceRuntime(
          dataSource, new RuntimeStatus(id, ConnectorState.CONNECTED, null, null));
    }
  }
}
