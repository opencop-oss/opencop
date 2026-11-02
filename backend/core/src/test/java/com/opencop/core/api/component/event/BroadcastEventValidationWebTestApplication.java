// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.api.component.support.CoreComponentWebTestConfig;
import com.opencop.core.api.event.business.policy.LayerBroadcastPolicy;
import com.opencop.core.api.event.business.policy.SourceBroadcastPolicy;
import com.opencop.core.api.event.business.usecase.event.BroadcastEventPublisher;
import com.opencop.core.api.event.business.usecase.event.LayerBroadcastEventHandler;
import com.opencop.core.api.event.business.usecase.event.SourceBroadcastEventHandler;
import com.opencop.core.api.event.ports.gateway.PostGisService;
import com.opencop.core.api.event.technical.endpoint.BroadcastEvent;
import com.opencop.core.api.event.technical.endpoint.SourceBroadcastEvent;
import com.opencop.core.api.general.business.model.user.InternalUser;
import com.opencop.core.api.general.business.policy.ResourceLifecycleGuard;
import com.opencop.core.api.general.ports.gateway.job.JobPublisher;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.api.iam.ports.usecase.access.me.UserSpecificManager;
import com.opencop.core.api.iam.ports.usecase.identity.UserManager;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.component.database.ComponentTestDatabaseConfig;
import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import com.opencop.core.sdk.ports.gateway.iam.SourceAccessRepository;
import java.util.UUID;
import org.geojson.Feature;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import({
  BroadcastEvent.class,
  SourceBroadcastEvent.class,
  CoreComponentWebTestConfig.class,
  ComponentTestDatabaseConfig.class,
  ResourceLifecycleGuard.class
})
public class BroadcastEventValidationWebTestApplication {

  @Bean
  BroadcastEventPublisher broadcastEventPublisher(
      MessageBrokerProducer<Feature> producer,
      UserManager<InternalUser> userManager,
      PostGisService postGisService) {
    return new BroadcastEventPublisher(producer, userManager, postGisService);
  }

  @Bean
  JobPublisher jobPublisher() {
    return mock(JobPublisher.class);
  }

  @Bean
  LayerBroadcastEventHandler layerBroadcaster(
      BroadcastEventPublisher publisher,
      UserSpecificManager userSpecificManager,
      ResourceLifecycleGuard resourceLifecycleGuard,
      JobPublisher jobPublisher) {
    return new LayerBroadcastEventHandler(
        publisher,
        new LayerBroadcastPolicy(userSpecificManager),
        resourceLifecycleGuard,
        jobPublisher);
  }

  @Bean
  SourceBroadcastEventHandler sourceBroadcaster(
      BroadcastEventPublisher publisher,
      SourceAccessRepository sourceAccessRepository,
      ResourceLifecycleGuard resourceLifecycleGuard,
      JobPublisher jobPublisher) {
    return new SourceBroadcastEventHandler(
        publisher,
        new SourceBroadcastPolicy(sourceAccessRepository),
        resourceLifecycleGuard,
        jobPublisher);
  }

  @Bean
  LayerManager<Layer, LayerMember> layerManager() {
    LayerManager<Layer, LayerMember> manager = mock(LayerManager.class);
    when(manager.get(any(UUID.class)))
        .thenAnswer(
            invocation -> {
              var layer = new Layer();
              layer.setId(invocation.getArgument(0));
              return layer;
            });
    return manager;
  }
}
