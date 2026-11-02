// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.sdk.model.watchbox.WatchboxAlertEvent;
import com.opencop.websocket.ports.gateway.websocket.SocketProducer;
import com.opencop.websocket.technical.spring.websocket.WatchboxSubscriptionReauthorizer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.DefaultMessage;
import tools.jackson.databind.ObjectMapper;

class WatchboxAlertsListenerTest {

  @Test
  void dropDecisionSuppressesBroadcast() throws Exception {
    var event = event();
    var om = new ObjectMapper();
    var producer = mock(SocketProducer.class);
    var reauthorizer = mock(WatchboxSubscriptionReauthorizer.class);
    when(reauthorizer.beforeDeliver(event.watchboxId(), destination(event.watchboxId())))
        .thenReturn(WatchboxSubscriptionReauthorizer.DeliveryDecision.DROP);

    new WatchboxAlertsListener(om, producer, reauthorizer)
        .onMessage(new DefaultMessage(new byte[0], om.writeValueAsBytes(event)), new byte[0]);

    verifyNoInteractions(producer);
  }

  @Test
  void deliverDecisionBroadcastsOriginalBytesOnce() throws Exception {
    var event = event();
    var om = new ObjectMapper();
    var producer = mock(SocketProducer.class);
    var reauthorizer = mock(WatchboxSubscriptionReauthorizer.class);
    byte[] body = om.writeValueAsBytes(event);
    String destination = destination(event.watchboxId());
    when(reauthorizer.beforeDeliver(event.watchboxId(), destination))
        .thenReturn(WatchboxSubscriptionReauthorizer.DeliveryDecision.DELIVER);

    new WatchboxAlertsListener(om, producer, reauthorizer)
        .onMessage(new DefaultMessage(new byte[0], body), new byte[0]);

    verify(producer).broadcast(destination, body, false);
  }

  @Test
  void deliverDecision_forwardsNewFieldsVerbatim() {
    var event = event();
    var om = new ObjectMapper();
    var producer = mock(SocketProducer.class);
    var reauthorizer = mock(WatchboxSubscriptionReauthorizer.class);
    var body = om.writeValueAsBytes(event);
    var destination = destination(event.watchboxId());
    when(reauthorizer.beforeDeliver(event.watchboxId(), destination))
        .thenReturn(WatchboxSubscriptionReauthorizer.DeliveryDecision.DELIVER);

    new WatchboxAlertsListener(om, producer, reauthorizer)
        .onMessage(new DefaultMessage(new byte[0], body), new byte[0]);

    verify(producer).broadcast(destination, body, false);
    var json = om.readTree(body);
    assertThat(json.get("detectionId").asText()).isEqualTo(event.detectionId().toString());
    assertThat(json.get("objectDisplayName").asText()).isEqualTo("Object 1");
  }

  @Test
  void legacyPayloadWithoutHandles_stillRoutes() {
    var body =
        """
        {"watchboxId":"00000000-0000-0000-0000-000000000001",
         "configEpoch":1,"sourceId":"00000000-0000-0000-0000-000000000002",
         "objectId":"object-1","eventId":"00000000-0000-0000-0000-000000000003",
         "effectiveEventTime":"2026-08-14T12:00:00Z","kind":"ENTER"}
        """
            .getBytes(StandardCharsets.UTF_8);
    var om = new ObjectMapper();
    var event = om.readValue(body, WatchboxAlertEvent.class);
    assertThat(event.detectionId()).isNull();
    assertThat(event.objectDisplayName()).isNull();
    var producer = mock(SocketProducer.class);
    var reauthorizer = mock(WatchboxSubscriptionReauthorizer.class);
    var destination = destination(event.watchboxId());
    when(reauthorizer.beforeDeliver(event.watchboxId(), destination))
        .thenReturn(WatchboxSubscriptionReauthorizer.DeliveryDecision.DELIVER);

    new WatchboxAlertsListener(om, producer, reauthorizer)
        .onMessage(new DefaultMessage(new byte[0], body), new byte[0]);

    verify(reauthorizer).beforeDeliver(event.watchboxId(), destination);
    verify(producer).broadcast(destination, body, false);
  }

  @Test
  void malformedPayloadLogsOnlyItsLengthAndStableClassification() {
    String canary = "watchbox-payload-canary-must-not-reach-logs";
    byte[] body = ("{\"canary\":\"" + canary + "\"").getBytes(StandardCharsets.UTF_8);
    var producer = mock(SocketProducer.class);
    var reauthorizer = mock(WatchboxSubscriptionReauthorizer.class);
    var listener = new WatchboxAlertsListener(new ObjectMapper(), producer, reauthorizer);
    var logger =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(WatchboxAlertsListener.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);

    try {
      listener.onMessage(new DefaultMessage(new byte[0], body), new byte[0]);
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }

    assertThat(appender.list).hasSize(1);
    var event = appender.list.getFirst();
    assertThat(event.getLevel()).isEqualTo(Level.WARN);
    assertThat(event.getFormattedMessage())
        .isEqualTo(
            "Invalid pubsub payload (" + body.length + " bytes, decode failure=MALFORMED_JSON)")
        .doesNotContain(canary);
    assertThat(event.getThrowableProxy()).isNull();
    verifyNoInteractions(producer, reauthorizer);
  }

  private static WatchboxAlertEvent event() {
    return new WatchboxAlertEvent(
        UUID.randomUUID(),
        1L,
        UUID.randomUUID(),
        "object-1",
        UUID.randomUUID(),
        Instant.parse("2026-08-14T12:00:00Z"),
        com.opencop.core.sdk.model.watchbox.WatchboxTransitionKind.ENTER,
        UUID.randomUUID(),
        "Object 1");
  }

  private static String destination(UUID watchboxId) {
    return "/topic/watchbox/" + watchboxId;
  }
}
