// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.gateway.cache.repair;

import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.scheduling.support.CronExpression;

@ConfigurationProperties("opencop.geo.directory-repair")
public record GeoDirectoryRepairProperties(
    @DefaultValue("0 0 9 * * *") String cron,
    @DefaultValue("America/New_York") String zone,
    @DefaultValue("1000") int scanCount,
    @DefaultValue("10ms") Duration scanPause,
    @DefaultValue("500") int registrationBatchSize) {
  public GeoDirectoryRepairProperties {
    if (!"-".equals(cron)) CronExpression.parse(cron);
    ZoneId.of(zone);
    if (scanCount <= 0 || registrationBatchSize <= 0 || scanPause.isNegative()) {
      throw new IllegalArgumentException("Invalid geo directory repair limits");
    }
    scanPause.toNanos(); // Reject durations that cannot be used by the interruptible wait.
  }
}
