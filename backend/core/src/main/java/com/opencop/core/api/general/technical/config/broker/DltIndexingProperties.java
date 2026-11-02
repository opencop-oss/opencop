// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config.broker;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "opencop.messaging-broker.dlt-indexing")
public class DltIndexingProperties {

  private Duration payloadRetention = Duration.ofDays(7);

  public Duration getPayloadRetention() {
    return payloadRetention;
  }

  public void setPayloadRetention(Duration payloadRetention) {
    this.payloadRetention = payloadRetention;
  }
}
