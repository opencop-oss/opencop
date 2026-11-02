// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.listener;

import com.opencop.core.sdk.model.watchbox.WatchboxAlertEvent;
import com.opencop.websocket.ports.gateway.websocket.SocketProducer;
import com.opencop.websocket.technical.spring.websocket.WatchboxSubscriptionReauthorizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public class WatchboxAlertsListener implements MessageListener {

  private static final Logger log = LoggerFactory.getLogger(WatchboxAlertsListener.class);
  private final ObjectMapper om;
  private final SocketProducer socketProducer;
  private final WatchboxSubscriptionReauthorizer reauthorizer;

  public WatchboxAlertsListener(
      ObjectMapper om,
      SocketProducer socketProducer,
      WatchboxSubscriptionReauthorizer reauthorizer) {
    this.om = om;
    this.socketProducer = socketProducer;
    this.reauthorizer = reauthorizer;
  }

  @Override
  public void onMessage(Message message, byte[] pattern) {
    byte[] body = message.getBody();
    try {
      // Decode only to route; the channel payload is already the JSON the browser receives.
      WatchboxAlertEvent event = om.readValue(body, WatchboxAlertEvent.class);

      String destination = "/topic/watchbox/" + event.watchboxId();
      // Before delivery, so a stale audience is re-authorized by the event that would otherwise
      // leak to it.
      switch (reauthorizer.beforeDeliver(event.watchboxId(), destination)) {
        case DELIVER -> socketProducer.broadcast(destination, body, false);
        case DROP -> {
          // The shared destination check did not resolve by its confidentiality deadline.
        }
      }
    } catch (JacksonException exception) {
      // Don't crash the listener thread. Payload content is never safe log metadata, even when it
      // is malformed; retain only its size and a stable decode classification.
      log.warn("Invalid pubsub payload ({} bytes, decode failure=MALFORMED_JSON)", body.length);
    } catch (RuntimeException exception) {
      // Decoded fine but enforcement or broadcast failed — a different incident than a bad
      // payload, so it must not wear the invalid-payload label. Single line at WARN (this can
      // repeat per event during an outage); the thread survives either way.
      log.warn(
          "Watchbox alert delivery failed; event not delivered to this instance's sessions: {}",
          exception.toString());
      log.debug("Watchbox alert delivery failure stack", exception);
    }
  }
}
