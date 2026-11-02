// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.notification;

import com.opencop.core.api.component.support.CoreComponentWebTestConfig;
import com.opencop.core.api.general.technical.endpoint.notification.NotificationsEndpoint;
import com.opencop.core.sdk.component.database.ComponentTestDatabaseConfig;
import com.opencop.core.sdk.technical.gateway.db.notification.NotificationEntity;
import com.opencop.core.sdk.technical.gateway.db.notification.NotificationRepository;
import com.opencop.core.sdk.technical.gateway.db.notification.RealTimeNotificationService;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackageClasses = NotificationEntity.class)
@EnableJpaRepositories(basePackageClasses = NotificationRepository.class)
@Import({
  ComponentTestDatabaseConfig.class,
  CoreComponentWebTestConfig.class,
  NotificationsEndpoint.class,
  RealTimeNotificationService.class
})
public class NotificationWebTestApplication {}
