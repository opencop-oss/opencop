// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.event;

import com.opencop.core.sdk.ports.gateway.broker.BrokerMessage;
import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import com.opencop.processor.business.model.event.EventEnvelope;
import com.opencop.processor.ports.business.usecase.event.ValidateEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.geojson.Feature;

public class ValidateGeoFeature implements ValidateEvent<Feature> {

  private final MessageBrokerProducer<Feature> producer;
  private final boolean keyedPublish;

  public ValidateGeoFeature(final MessageBrokerProducer<Feature> producer, boolean keyedPublish) {
    this.producer = producer;
    this.keyedPublish = keyedPublish;
  }

  @Override
  public void validate(List<EventEnvelope<Feature>> inputs) {
    var validFeatures = new ArrayList<BrokerMessage<Feature>>();
    for (EventEnvelope<Feature> input : inputs) {
      var feature = input.event();
      OpenCopProperties properties = OpenCopPropertiesParser.readFromFeature(feature);
      if (properties != null) {
        FeatureStartAtSupport.ensureStartAt(properties);
        feature.setProperty(OpenCopPropertiesParser.OPENCOP_NODE, properties);
      }
      validFeatures.add(message(feature, properties, input.metadata()));
    }
    if (!validFeatures.isEmpty()) {
      producer.sendAll(validFeatures);
    }
  }

  private BrokerMessage<Feature> message(
      Feature feature, OpenCopProperties properties, Map<String, String> metadata) {
    var key = keyedPublish ? EventPartitionKeys.keyFor(properties) : null;
    return key == null
        ? BrokerMessage.of(feature, metadata)
        : BrokerMessage.withKey(key, feature, metadata);
  }
}
