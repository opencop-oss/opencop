// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config.byod;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("opencop.byod.consumer")
public record ByodConsumerProperties(
    @DefaultValue("100") int maxPollRecords,
    @DefaultValue("2") int retryAttempts,
    @DefaultValue("1000") long retryBackoffMs,
    @DefaultValue("earliest") String autoOffsetReset,
    @DefaultValue("500") int maxContainers,
    @DefaultValue("45000") int sessionTimeoutMs,
    @DefaultValue("3000") int heartbeatIntervalMs,
    @DefaultValue("300000") int maxPollIntervalMs,
    @DefaultValue("1") int fetchMinBytes,
    @DefaultValue("52428800") int fetchMaxBytes) {}
