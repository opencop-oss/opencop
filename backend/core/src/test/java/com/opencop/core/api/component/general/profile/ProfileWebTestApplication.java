// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.profile;

import com.opencop.core.api.component.support.CoreComponentWebTestConfig;
import com.opencop.core.api.general.business.factory.profile.MapperProfileResponseFactory;
import com.opencop.core.api.general.business.usecase.profile.mapping.DeleteMappingProfileUsecase;
import com.opencop.core.api.general.business.usecase.profile.mapping.ListMappingProfilesUsecase;
import com.opencop.core.api.general.business.usecase.profile.mapping.MapperAuditProjection;
import com.opencop.core.api.general.business.usecase.profile.mapping.SaveMappingProfileUsecase;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.usecase.profile.mapping.DeleteMappingProfile;
import com.opencop.core.api.general.ports.usecase.profile.mapping.ListMappingProfiles;
import com.opencop.core.api.general.ports.usecase.profile.mapping.SaveMappingProfile;
import com.opencop.core.api.general.technical.endpoint.profile.mapper.MapperProfileEndpoints;
import com.opencop.core.api.general.technical.endpoint.profile.mapper.PreviewMapperProfile;
import com.opencop.core.api.general.technical.gateway.db.connection.PatchableDataSourceEntity;
import com.opencop.core.api.general.technical.gateway.db.profile.JpaMapperCatalogGateway;
import com.opencop.core.api.general.technical.gateway.db.profile.MapperCatalogEntryEntity;
import com.opencop.core.sdk.component.database.ComponentTestDatabaseConfig;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.technical.gateway.profile.MapperProfileRepository;
import com.opencop.core.sdk.technical.mapper.MapperFacade;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import tools.jackson.databind.ObjectMapper;

@SpringBootConfiguration
@EnableAutoConfiguration
@EnableMethodSecurity
@EntityScan(basePackageClasses = {MapperCatalogEntryEntity.class, PatchableDataSourceEntity.class})
@ComponentScan(basePackageClasses = MapperFacade.class)
@Import({
  ComponentTestDatabaseConfig.class,
  CoreComponentWebTestConfig.class,
  MapperProfileEndpoints.class,
  PreviewMapperProfile.class,
  MapperProfileRepository.class,
  JpaMapperCatalogGateway.class
})
public class ProfileWebTestApplication {

  @Bean
  MapperProfileResponseFactory mapperProfileResponseFactory() {
    return new MapperProfileResponseFactory();
  }

  @Bean
  SaveMappingProfile saveMappingProfile(
      MapperProfileService mapperProfileService,
      AuditRecorder auditRecorder,
      MapperAuditProjection projection) {
    return new SaveMappingProfileUsecase(mapperProfileService, auditRecorder, projection);
  }

  @Bean
  ListMappingProfiles listMappingProfiles(MapperProfileService mapperProfileService) {
    return new ListMappingProfilesUsecase(mapperProfileService);
  }

  @Bean
  DeleteMappingProfile deleteMappingProfile(
      MapperProfileService mapperProfileService,
      AuditRecorder auditRecorder,
      MapperAuditProjection projection) {
    return new DeleteMappingProfileUsecase(mapperProfileService, auditRecorder, projection);
  }

  // Mocked: these profile tests assert profile behaviour, and a real recorder would demand the
  // audit schema and an enclosing transaction. The recorder wiring is proved by its own test.
  @Bean
  AuditRecorder auditRecorder() {
    return org.mockito.Mockito.mock(AuditRecorder.class);
  }

  @Bean
  MapperAuditProjection mapperAuditProjection(ObjectMapper objectMapper) {
    return new MapperAuditProjection();
  }
}
