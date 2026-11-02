// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.event;

import com.opencop.processor.ports.business.usecase.event.RealTimeEventHandler;
import com.opencop.processor.ports.technical.gateway.cache.GeoCacheGateway;
import java.util.List;
import org.geojson.Feature;

public class RealTimeGeoFeatureHandler implements RealTimeEventHandler<List<Feature>> {

  private final GeoCacheGateway cacheWriter;

  public RealTimeGeoFeatureHandler(GeoCacheGateway cacheWriter) {
    this.cacheWriter = cacheWriter;
  }

  @Override
  public void process(List<Feature> inputs) {
    // Unlike persistence, this path delegates to cache writing that skips deterministic bad
    // features locally; broad infrastructure failures should still bubble out for retry/DLT.
    cacheWriter.write(inputs, true);
  }
}
