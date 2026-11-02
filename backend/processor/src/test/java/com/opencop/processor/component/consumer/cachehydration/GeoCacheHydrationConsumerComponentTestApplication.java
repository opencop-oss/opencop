// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.consumer.cachehydration;

import com.opencop.processor.component.support.ProcessorComponentTestDatabaseConfig;
import com.opencop.processor.component.support.ProcessorKafkaComponentTestConfig;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@Import({
  ProcessorKafkaComponentTestConfig.class,
  ProcessorComponentTestDatabaseConfig.class,
  GeoCacheHydrationConsumerComponentTestContext.class
})
public class GeoCacheHydrationConsumerComponentTestApplication {}
