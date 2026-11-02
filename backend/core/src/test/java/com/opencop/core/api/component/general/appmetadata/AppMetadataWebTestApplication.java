// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.appmetadata;

import com.opencop.core.api.component.support.CoreComponentWebTestConfig;
import com.opencop.core.api.general.technical.config.RuntimeFeatureConfig;
import com.opencop.core.api.general.technical.endpoint.appmetadata.AppMetadataEndpoint;
import com.opencop.core.api.general.technical.endpoint.appmetadata.BuildMetadataConfiguration;
import com.opencop.core.api.general.technical.endpoint.runtimefeature.RuntimeFeatureEndpoint;
import com.opencop.core.api.general.technical.gateway.db.runtimefeature.JpaWatchboxTrackingResetGateway;
import com.opencop.core.api.general.technical.observability.runtimefeature.MicrometerWatchboxTrackingResetTelemetry;
import com.opencop.core.sdk.component.database.ComponentTestDatabaseConfig;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.LayerEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.member.LayerMemberEntity;
import com.opencop.core.sdk.technical.gateway.db.runtimefeature.RuntimeFeatureEntity;
import com.opencop.core.sdk.technical.gateway.db.runtimefeature.RuntimeFeatureJpaRepository;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxEntity;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxJpaRepository;
import java.time.Clock;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(
    basePackageClasses = {
      RuntimeFeatureEntity.class,
      GroupEntity.class,
      LayerEntity.class,
      LayerMemberEntity.class,
      WatchboxEntity.class
    })
@EnableJpaRepositories(
    basePackageClasses = {
      RuntimeFeatureJpaRepository.class,
      IamGroupRepository.class,
      WatchboxJpaRepository.class
    })
@EnableMethodSecurity
@Import({
  ComponentTestDatabaseConfig.class,
  CoreComponentWebTestConfig.class,
  AppMetadataEndpoint.class,
  RuntimeFeatureEndpoint.class,
  RuntimeFeatureConfig.class,
  JpaWatchboxTrackingResetGateway.class,
  MicrometerWatchboxTrackingResetTelemetry.class,
  BuildMetadataConfiguration.class
})
public class AppMetadataWebTestApplication {

  @Bean
  Clock runtimeFeatureClock() {
    return Clock.systemUTC();
  }
}
