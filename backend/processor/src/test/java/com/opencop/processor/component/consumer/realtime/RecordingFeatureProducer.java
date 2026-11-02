// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.consumer.realtime;

import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.geojson.Feature;

public class RecordingFeatureProducer implements MessageBrokerProducer<Feature> {

  private final BlockingQueue<Feature> sentFeatures = new LinkedBlockingQueue<>();
  private final BlockingQueue<Feature> dltFeatures = new LinkedBlockingQueue<>();

  @Override
  public void send(Feature event, Map<String, String> headers) {
    sentFeatures.add(event);
  }

  @Override
  public void sendDlt(Feature event) {
    dltFeatures.add(event);
  }

  @Override
  public void send(Feature event) {
    sentFeatures.add(event);
  }

  public Feature awaitPublished() throws InterruptedException {
    var feature = pollPublished(Duration.ofSeconds(10));
    if (feature == null) {
      throw new AssertionError("Timed out waiting for mapped feature publication");
    }
    return feature;
  }

  public Feature pollPublished(Duration timeout) throws InterruptedException {
    return sentFeatures.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
  }

  public List<Feature> published() {
    return List.copyOf(sentFeatures);
  }

  public List<Feature> dltPublished() {
    return List.copyOf(dltFeatures);
  }

  public void reset() {
    sentFeatures.clear();
    dltFeatures.clear();
  }
}
