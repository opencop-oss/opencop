// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.banner;

import com.opencop.core.api.component.support.CoreComponentWebTestConfig;
import com.opencop.core.api.general.technical.endpoint.banner.BannersEndpoint;
import com.opencop.core.api.general.technical.gateway.db.banner.AppBannerService;
import com.opencop.core.api.general.technical.gateway.db.banner.BannerEntity;
import com.opencop.core.api.general.technical.gateway.db.banner.BannerRepository;
import com.opencop.core.api.general.technical.gateway.db.banner.BannerTranslationEntity;
import com.opencop.core.api.general.technical.gateway.db.banner.BannerTranslationRepository;
import com.opencop.core.sdk.component.database.ComponentTestDatabaseConfig;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootConfiguration
@EnableAutoConfiguration
@EnableMethodSecurity
@EntityScan(basePackageClasses = {BannerEntity.class, BannerTranslationEntity.class})
@EnableJpaRepositories(
    basePackageClasses = {BannerRepository.class, BannerTranslationRepository.class})
@Import({
  BannersEndpoint.class,
  AppBannerService.class,
  CoreComponentWebTestConfig.class,
  ComponentTestDatabaseConfig.class
})
public class BannerWebTestApplication {}
