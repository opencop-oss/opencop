// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.event;

import static org.mockito.Mockito.mock;

import com.opencop.core.api.component.support.CoreComponentTestDatabaseConfig;
import com.opencop.core.api.component.support.CoreComponentWebTestConfig;
import com.opencop.core.api.event.technical.simulation.HistoricalSimulationEndpoint;
import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackageClasses = HistoricalSimulationEndpoint.class)
@Import({CoreComponentWebTestConfig.class, CoreComponentTestDatabaseConfig.class})
public class HistoricalSimulationEndpointTestApplication {

  @Bean
  @Qualifier("rawEventProducer")
  @SuppressWarnings("unchecked")
  MessageBrokerProducer<byte[]> rawEventProducer() {
    return mock(MessageBrokerProducer.class);
  }
}
