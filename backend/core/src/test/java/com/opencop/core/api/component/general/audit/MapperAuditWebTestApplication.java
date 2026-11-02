// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.audit;

import com.opencop.core.api.component.support.CoreComponentWebTestConfig;
import com.opencop.core.api.general.business.usecase.profile.mapping.DeleteMappingProfileUsecase;
import com.opencop.core.api.general.business.usecase.profile.mapping.MapperAuditProjection;
import com.opencop.core.api.general.business.usecase.profile.mapping.SaveMappingProfileUsecase;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.usecase.profile.mapping.DeleteMappingProfile;
import com.opencop.core.api.general.ports.usecase.profile.mapping.SaveMappingProfile;
import com.opencop.core.api.general.technical.gateway.db.audit.JpaAuditHistoryStore;
import com.opencop.core.sdk.component.database.ComponentTestDatabaseConfig;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.technical.gateway.db.audit.AuditHistoryEntity;
import com.opencop.core.sdk.technical.gateway.db.audit.AuditHistoryJpaRepository;
import com.opencop.core.sdk.technical.gateway.profile.MapperProfileRepository;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import tools.jackson.databind.ObjectMapper;

/**
 * The mapper save and archive paths wired to the real audit store.
 *
 * <p>Separate from {@link AuditWebTestApplication}, which serves the read endpoint only, and from
 * the profile component application, which mocks the recorder: proving a change and its history row
 * commit together needs both halves real.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackageClasses = AuditHistoryEntity.class)
@EnableJpaRepositories(basePackageClasses = AuditHistoryJpaRepository.class)
@Import({
  ComponentTestDatabaseConfig.class,
  CoreComponentWebTestConfig.class,
  MapperProfileRepository.class,
  JpaAuditHistoryStore.class
})
public class MapperAuditWebTestApplication {

  @Bean
  MapperAuditProjection mapperAuditProjection(ObjectMapper objectMapper) {
    return new MapperAuditProjection();
  }

  @Bean
  SaveMappingProfile saveMappingProfile(
      MapperProfileService service, AuditRecorder recorder, MapperAuditProjection projection) {
    return new SaveMappingProfileUsecase(service, recorder, projection);
  }

  @Bean
  DeleteMappingProfile deleteMappingProfile(
      MapperProfileService service, AuditRecorder recorder, MapperAuditProjection projection) {
    return new DeleteMappingProfileUsecase(service, recorder, projection);
  }
}
