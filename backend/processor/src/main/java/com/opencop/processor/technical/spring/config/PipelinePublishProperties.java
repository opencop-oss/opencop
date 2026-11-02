// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Per-object partition keying for the mapped and validated publish hops. Default on.
 *
 * <p>Turning this off is the operational kill switch: both hops revert to key-less spread
 * publishing (the pre-keying placement, safe from hot partitions by construction), at the cost of
 * the per-object delivery-order guarantee for conforming BYOD producers. Watchbox detection then
 * relies solely on its order-tolerant fold; watch the engine's {@code stale_dropped_transition}
 * fold outcome while the switch is off — sustained movement there is the cost being paid.
 */
@ConfigurationProperties("opencop.pipeline.publish")
public class PipelinePublishProperties {

  private boolean keyed = true;

  public boolean isKeyed() {
    return keyed;
  }

  public void setKeyed(boolean keyed) {
    this.keyed = keyed;
  }
}
