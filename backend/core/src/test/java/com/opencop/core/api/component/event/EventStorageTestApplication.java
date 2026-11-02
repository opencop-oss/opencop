// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.event;

import com.opencop.core.api.general.technical.gateway.db.remarks.RemarkEntity;
import com.opencop.core.api.general.technical.gateway.db.remarks.RemarkJpaRepository;
import com.opencop.core.api.general.technical.gateway.db.remarks.RemarkRepository;
import com.opencop.core.api.general.technical.gateway.scim.group.InternalGroupEntity;
import com.opencop.core.api.general.technical.gateway.scim.relationship.InternalUserGroupRelationshipEntity;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import com.opencop.core.sdk.component.database.ComponentTestDatabaseConfig;
import com.opencop.core.sdk.technical.gateway.db.media.EventAttachmentEntity;
import com.opencop.core.sdk.technical.gateway.db.media.EventAttachmentJpaRepository;
import com.opencop.core.sdk.technical.gateway.db.media.JpaEventAttachmentRepository;
import com.opencop.core.sdk.technical.gateway.db.media.MediaAttachmentEntity;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(
    basePackageClasses = {
      EventAttachmentEntity.class,
      MediaAttachmentEntity.class,
      RemarkEntity.class,
      InternalUserEntity.class,
      InternalGroupEntity.class,
      InternalUserGroupRelationshipEntity.class
    })
@EnableJpaRepositories(
    basePackageClasses = {EventAttachmentJpaRepository.class, RemarkJpaRepository.class})
@Import({
  ComponentTestDatabaseConfig.class,
  JpaEventAttachmentRepository.class,
  RemarkRepository.class
})
public class EventStorageTestApplication {}
