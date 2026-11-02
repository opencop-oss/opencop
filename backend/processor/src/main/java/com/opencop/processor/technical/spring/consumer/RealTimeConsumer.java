// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.consumer;

import com.opencop.processor.ports.business.usecase.event.RealTimeEventHandler;
import java.util.List;
import org.geojson.Feature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class RealTimeConsumer {

  private static final Logger log = LoggerFactory.getLogger(RealTimeConsumer.class);

  private final RealTimeEventHandler<List<Feature>> usecase;

  RealTimeConsumer(final RealTimeEventHandler<List<Feature>> usecase) {
    this.usecase = usecase;
  }

  @KafkaListener(
      id = "realTimeConsumer",
      topics = "${opencop.messaging-broker.listeners.realTime.topic}",
      containerFactory = "realTimeFactory")
  public void realTimeEvent(List<Feature> features) {
    long startedAtNanos = System.nanoTime();
    log.debug("Consuming real-time batch: count={}", features.size());

    try {
      usecase.process(features);
      log.info(
          "Real-time batch processed successfully: count={}, durationMs={}",
          features.size(),
          ConsumerLogSupport.elapsedMillisSince(startedAtNanos));
    } catch (Exception e) {
      log.error(
          "Real-time batch failed: count={}, durationMs={}, featureIds={}",
          features.size(),
          ConsumerLogSupport.elapsedMillisSince(startedAtNanos),
          ConsumerLogSupport.featureIds(features),
          e);
      throw e;
    }
  }
}
