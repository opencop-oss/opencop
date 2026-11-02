// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.support;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@Import({ProcessorKafkaComponentTestConfig.class, ProcessorComponentTestDatabaseConfig.class})
public class ProcessorKafkaDatabaseComponentTestApplication {}
