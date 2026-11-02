// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.technical.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.api.watchbox.business.service.WatchboxDevStatusOverrideService;
import com.opencop.core.api.watchbox.ports.usecase.ManageWatchboxes;
import com.opencop.core.sdk.model.watchbox.WatchboxAlertingStatus;
import com.opencop.core.sdk.model.watchbox.WatchboxCapabilities;
import com.opencop.core.sdk.model.watchbox.WatchboxDirection;
import com.opencop.core.sdk.model.watchbox.WatchboxFilter;
import com.opencop.core.sdk.model.watchbox.WatchboxResponse;
import com.opencop.core.sdk.model.watchbox.WatchboxStatusOverrideRequest;
import com.opencop.core.sdk.model.watchbox.WatchboxStatusOverrideSourceStatus;
import com.opencop.core.sdk.model.watchbox.WatchboxStatusReason;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class WatchboxDevStatusOverrideEndpointTest {

  private final UUID watchboxId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final UUID sourceId = UUID.randomUUID();
  private final ManageWatchboxes usecase = mock(ManageWatchboxes.class);
  private final WatchboxDevStatusOverrideService overrideService =
      new WatchboxDevStatusOverrideService();
  private final WatchboxDevStatusOverrideEndpoint endpoint =
      new WatchboxDevStatusOverrideEndpoint(usecase, overrideService);
  private Logger logger;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void setUp() {
    logger = (Logger) LoggerFactory.getLogger(WatchboxDevStatusOverrideEndpoint.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
    appender.stop();
  }

  @Test
  void setLogsBoundedOverrideRecordAndAppliesManagerShape() {
    when(usecase.get(org.mockito.Mockito.eq(watchboxId), org.mockito.Mockito.any()))
        .thenReturn(visibleWatchbox());

    var response = endpoint.set(watchboxId, overrideRequest(), principal()).getBody();

    assertThat(response).isNotNull();
    assertThat(response.sourceStatuses()).hasSize(1);
    var message = onlyLogMessage();
    assertThat(message).contains("WATCHBOX_DEV_OVERRIDE operation=set");
    assertThat(message).contains("watchbox_id=" + watchboxId);
    assertThat(message).contains("actor_user_id=" + userId);
    assertThat(message).contains("alerting_status=FAILED");
    assertThat(message).contains("source_status_count=1");
    assertNoPayloadMaterial(message);
  }

  @Test
  void clearLogsBoundedOverrideRecord() {
    endpoint.delete(watchboxId, principal());

    var message = onlyLogMessage();
    assertThat(message).contains("WATCHBOX_DEV_OVERRIDE operation=clear");
    assertThat(message).contains("watchbox_id=" + watchboxId);
    assertThat(message).contains("actor_user_id=" + userId);
    assertNoPayloadMaterial(message);
  }

  private String onlyLogMessage() {
    assertThat(appender.list).hasSize(1);
    return appender.list.getFirst().getFormattedMessage();
  }

  private void assertNoPayloadMaterial(String message) {
    assertThat(message).doesNotContain("fixture source");
    assertThat(message).doesNotContain("watch name");
    assertThat(message).doesNotContain("watch description");
    assertThat(message).doesNotContain("{\"");
  }

  private WatchboxStatusOverrideRequest overrideRequest() {
    return new WatchboxStatusOverrideRequest(
        WatchboxAlertingStatus.FAILED,
        Set.of(WatchboxStatusReason.WATCHBOX_SOURCE_OBJECT_ID_UNSTABLE_OR_UNKNOWN),
        List.of(
            new WatchboxStatusOverrideSourceStatus(
                sourceId,
                "fixture source",
                WatchboxAlertingStatus.FAILED,
                WatchboxStatusReason.WATCHBOX_SOURCE_OBJECT_ID_UNSTABLE_OR_UNKNOWN,
                null,
                1001L,
                1000L,
                null)));
  }

  private WatchboxResponse visibleWatchbox() {
    return new WatchboxResponse(
        watchboxId,
        "watch name",
        "watch description",
        true,
        WatchboxDirection.BOTH,
        null,
        WatchboxFilter.empty(),
        Set.of(UUID.randomUUID()),
        7L,
        WatchboxAlertingStatus.ACTIVE,
        Set.of(),
        List.of(),
        new WatchboxCapabilities(true, true),
        Instant.parse("2026-06-26T01:00:00Z"),
        Instant.parse("2026-06-26T01:00:00Z"));
  }

  private OpenCopPrincipal principal() {
    return com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(userId, true);
  }
}
