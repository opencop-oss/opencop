// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.event;

import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;

/**
 * Canonical {@code source:objectId} partition key shared by the mapped and validated publish hops.
 *
 * <p>A key pins every report about one tracked object to one partition per topic, so a conforming
 * BYOD producer's per-object publication order survives end to end (keyed ingress partition →
 * single decode worker → keyed mapped partition → single validation worker → keyed validated
 * partition → single watchbox listener child). A null return means the feature has no stable
 * identity and therefore no per-object ordering domain to preserve; the broker's spread partitioner
 * then places it, which is also the correct fallback for every feature when the {@code
 * opencop.pipeline.publish.keyed} kill switch is off.
 */
public final class EventPartitionKeys {

  private EventPartitionKeys() {}

  public static String keyFor(OpenCopProperties properties) {
    if (properties == null
        || properties.getSource() == null
        || properties.getObjectId() == null
        || properties.getObjectId().isBlank()) {
      return null;
    }
    return properties.getSource() + ":" + properties.getObjectId();
  }
}
