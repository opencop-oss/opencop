// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config;

import com.opencop.core.api.general.business.factory.profile.MapperProfileResponseFactory;
import com.opencop.core.api.general.business.usecase.profile.mapping.DeleteMappingProfileUsecase;
import com.opencop.core.api.general.business.usecase.profile.mapping.ListMappingProfilesUsecase;
import com.opencop.core.api.general.business.usecase.profile.mapping.MapperAuditProjection;
import com.opencop.core.api.general.business.usecase.profile.mapping.SaveMappingProfileUsecase;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.usecase.profile.mapping.DeleteMappingProfile;
import com.opencop.core.api.general.ports.usecase.profile.mapping.ListMappingProfiles;
import com.opencop.core.api.general.ports.usecase.profile.mapping.SaveMappingProfile;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProfilesConfig {

  @Bean
  MapperProfileResponseFactory mapperProfileResponseFactory() {
    return new MapperProfileResponseFactory();
  }

  @Bean
  SaveMappingProfile saveMapperProfile(
      final MapperProfileService service,
      final AuditRecorder auditRecorder,
      final MapperAuditProjection projection) {
    return new SaveMappingProfileUsecase(service, auditRecorder, projection);
  }

  @Bean
  ListMappingProfiles listMappingProfiles(final MapperProfileService service) {
    return new ListMappingProfilesUsecase(service);
  }

  @Bean
  DeleteMappingProfile deleteMapperProfile(
      final MapperProfileService service,
      final AuditRecorder auditRecorder,
      final MapperAuditProjection projection) {
    return new DeleteMappingProfileUsecase(service, auditRecorder, projection);
  }
}
