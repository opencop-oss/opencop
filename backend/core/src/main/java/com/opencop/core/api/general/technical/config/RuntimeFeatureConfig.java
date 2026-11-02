// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config;

import com.opencop.core.api.general.business.policy.runtimefeature.RuntimeFeatureAudiencePolicy;
import com.opencop.core.api.general.business.policy.runtimefeature.RuntimeFeatureNamePolicy;
import com.opencop.core.api.general.business.usecase.runtimefeature.CheckRuntimeFeatureAvailabilityUsecase;
import com.opencop.core.api.general.business.usecase.runtimefeature.GetRuntimeFeatureMetadataUsecase;
import com.opencop.core.api.general.business.usecase.runtimefeature.ManageRuntimeFeaturesUsecase;
import com.opencop.core.api.general.ports.gateway.runtimefeature.RuntimeFeatureUserGroupAccess;
import com.opencop.core.api.general.ports.gateway.runtimefeature.WatchboxTrackingResetGateway;
import com.opencop.core.api.general.ports.gateway.runtimefeature.WatchboxTrackingResetTelemetry;
import com.opencop.core.api.general.ports.usecase.runtimefeature.CheckRuntimeFeatureAvailability;
import com.opencop.core.api.general.ports.usecase.runtimefeature.GetRuntimeFeatureMetadata;
import com.opencop.core.api.general.ports.usecase.runtimefeature.ManageRuntimeFeatures;
import com.opencop.core.api.general.technical.gateway.iam.IamRuntimeFeatureUserGroupAccess;
import com.opencop.core.sdk.ports.gateway.runtimefeature.RuntimeFeatureMutationGateway;
import com.opencop.core.sdk.ports.gateway.runtimefeature.RuntimeFeatureStateReader;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.runtimefeature.JpaRuntimeFeatureStore;
import com.opencop.core.sdk.technical.gateway.db.runtimefeature.RuntimeFeatureAudienceUserGroupJpaRepository;
import com.opencop.core.sdk.technical.gateway.db.runtimefeature.RuntimeFeatureHistoryJpaRepository;
import com.opencop.core.sdk.technical.gateway.db.runtimefeature.RuntimeFeatureJpaRepository;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class RuntimeFeatureConfig {

  @Bean
  public JpaRuntimeFeatureStore runtimeFeatureStore(
      RuntimeFeatureJpaRepository featureRepository,
      RuntimeFeatureAudienceUserGroupJpaRepository audienceUserGroupRepository,
      RuntimeFeatureHistoryJpaRepository historyRepository,
      ObjectMapper objectMapper,
      Clock clock) {
    return new JpaRuntimeFeatureStore(
        featureRepository, audienceUserGroupRepository, historyRepository, objectMapper, clock);
  }

  @Bean
  public RuntimeFeatureUserGroupAccess runtimeFeatureUserGroupAccess(
      IamGroupRepository groupRepository, IamGroupMembersRepository groupMembersRepository) {
    return new IamRuntimeFeatureUserGroupAccess(groupRepository, groupMembersRepository);
  }

  @Bean
  public RuntimeFeatureAudiencePolicy runtimeFeatureAudiencePolicy(
      RuntimeFeatureUserGroupAccess userGroupAccess) {
    return new RuntimeFeatureAudiencePolicy(userGroupAccess);
  }

  @Bean
  public GetRuntimeFeatureMetadata getRuntimeFeatureMetadata(
      JpaRuntimeFeatureStore store, RuntimeFeatureAudiencePolicy audiencePolicy) {
    RuntimeFeatureStateReader stateReader = store;
    return new GetRuntimeFeatureMetadataUsecase(stateReader, audiencePolicy);
  }

  @Bean
  public CheckRuntimeFeatureAvailability checkRuntimeFeatureAvailability(
      JpaRuntimeFeatureStore store, RuntimeFeatureAudiencePolicy audiencePolicy) {
    RuntimeFeatureStateReader stateReader = store;
    return new CheckRuntimeFeatureAvailabilityUsecase(stateReader, audiencePolicy);
  }

  @Bean
  public RuntimeFeatureNamePolicy runtimeFeatureNamePolicy() {
    return new RuntimeFeatureNamePolicy();
  }

  @Bean
  public ManageRuntimeFeatures manageRuntimeFeatures(
      JpaRuntimeFeatureStore store,
      RuntimeFeatureAudiencePolicy audiencePolicy,
      RuntimeFeatureNamePolicy namePolicy,
      WatchboxTrackingResetGateway trackingResetGateway,
      WatchboxTrackingResetTelemetry trackingResetTelemetry) {
    RuntimeFeatureMutationGateway mutationGateway = store;
    return new ManageRuntimeFeaturesUsecase(
        mutationGateway, audiencePolicy, namePolicy, trackingResetGateway, trackingResetTelemetry);
  }
}
