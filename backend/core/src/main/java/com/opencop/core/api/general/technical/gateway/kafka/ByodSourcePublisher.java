// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.kafka;

import com.opencop.core.api.general.ports.gateway.kafka.ByodSourceStatePublisher;
import com.opencop.core.sdk.model.datasource.ByodSourceState;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

/** Publishes {@link ByodSourceState} to the compacted topic for Processor consumption. */
public class ByodSourcePublisher implements ByodSourceStatePublisher {

  private static final Logger log = LoggerFactory.getLogger(ByodSourcePublisher.class);

  private final KafkaTemplate<String, ByodSourceState> template;
  private final String topic;

  public ByodSourcePublisher(KafkaTemplate<String, ByodSourceState> template, String topic) {
    this.template = template;
    this.topic = topic;
  }

  /**
   * Synchronous publish with 5-second timeout. This runs on admin endpoints
   * (start/stop/delete/add), not a hot path. The caller needs confirmation that the state change
   * reached the compacted topic — going async would just mask failures without cancelling the
   * in-flight write.
   */
  @Override
  public void publish(ByodSourceState state) {
    Objects.requireNonNull(state.sourceId(), "sourceId must not be null");
    var key = state.sourceId().toString();
    try {
      template.send(topic, key, state).get(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Failed to publish BYOD source state", e);
    } catch (Exception e) {
      log.error(
          "Failed to publish {} for sourceId={} to {}", state.status(), state.sourceId(), topic, e);
      throw new RuntimeException("Failed to publish BYOD source state", e);
    }
    log.info("Published {} for sourceId={} to {}", state.status(), state.sourceId(), topic);
  }
}
