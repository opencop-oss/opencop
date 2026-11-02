// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.support;

import com.opencop.core.api.common.CoreSecurityExceptionAdvice;
import com.opencop.core.sdk.component.config.TestSecurityConfig;
import com.opencop.core.sdk.technical.exception.GlobalExceptionManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@TestConfiguration(proxyBeanMethods = false)
@Import({GlobalExceptionManager.class, CoreSecurityExceptionAdvice.class, TestSecurityConfig.class})
@ActiveProfiles("it")
public class CoreComponentWebTestConfig {}
