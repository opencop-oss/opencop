// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.watchbox;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.FullyQualifiedConfigurationContextInitializer;
import com.opencop.core.api.watchbox.business.service.WatchboxDevStatusOverrideService;
import com.opencop.core.api.watchbox.technical.endpoint.WatchboxDevStatusOverrideEndpoint;
import com.opencop.core.sdk.component.security.TestAuthentications;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(initializers = FullyQualifiedConfigurationContextInitializer.class)
class WatchboxDevStatusOverrideEndpointDisabledIT {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655442101");
  private static final String ROUTE = "/v1/dev/watchboxes/{watchboxId}/status-override";

  @Nested
  @SpringBootTest(
      classes = WatchboxWebTestApplication.class,
      webEnvironment = SpringBootTest.WebEnvironment.MOCK,
      properties = "opencop.dev.mode=false")
  @ActiveProfiles("it")
  class DevModeFalse extends AbstractWebIT {

    @Autowired private ApplicationContext applicationContext;

    @Test
    void statusOverride_whenDevModeFalse_returnsNotFoundAndDoesNotLoadBeans() throws Exception {
      assertDevBeansNotLoaded(applicationContext);

      mockMvc
          .perform(
              put(ROUTE, UUID.randomUUID())
                  .with(csrf())
                  .with(authentication(TestAuthentications.oidcAdmin(USER_ID)))
                  .contentType("application/json")
                  .content(
                      """
                      {"alertingStatus":"FAILED","alertingReasons":[],"sourceStatuses":[]}
                      """))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  @SpringBootTest(
      classes = WatchboxWebTestApplication.class,
      webEnvironment = SpringBootTest.WebEnvironment.MOCK,
      properties = "spring.config.name=application-it")
  @ActiveProfiles("it")
  class DevModeMissing extends AbstractWebIT {

    @Autowired private ApplicationContext applicationContext;

    @Test
    void statusOverride_whenDevModeMissing_returnsNotFoundAndDoesNotLoadBeans() throws Exception {
      assertDevBeansNotLoaded(applicationContext);

      mockMvc
          .perform(
              put(ROUTE, UUID.randomUUID())
                  .with(csrf())
                  .with(authentication(TestAuthentications.oidcAdmin(USER_ID)))
                  .contentType("application/json")
                  .content(
                      """
                      {"alertingStatus":"FAILED","alertingReasons":[],"sourceStatuses":[]}
                      """))
          .andExpect(status().isNotFound());
    }
  }

  private static void assertDevBeansNotLoaded(ApplicationContext applicationContext) {
    assertFalse(applicationContext.containsBeanDefinition("watchboxDevStatusOverrideEndpoint"));
    assertFalse(applicationContext.containsBeanDefinition("watchboxDevStatusOverrideService"));
    assertTrue(
        applicationContext.getBeansOfType(WatchboxDevStatusOverrideEndpoint.class).isEmpty());
    assertTrue(applicationContext.getBeansOfType(WatchboxDevStatusOverrideService.class).isEmpty());
  }
}
