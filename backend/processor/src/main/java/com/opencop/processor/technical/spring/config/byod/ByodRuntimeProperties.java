// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config.byod;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("opencop.byod.runtime")
public record ByodRuntimeProperties(
    @DefaultValue("5s") Duration flushInterval, @DefaultValue("5m") Duration statusTtl) {}
