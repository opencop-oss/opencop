// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.event;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.event.technical.simulation.HistoricalSimulationEndpoint;
import com.opencop.core.api.event.technical.simulation.HistoricalSimulationService;
import com.opencop.core.sdk.component.security.TestAuthentications;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

class HistoricalSimulationEndpointDisabledIT {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440302");
  private static final String ROUTE = "/v1/history/test-data/generate";

  @Nested
  @SpringBootTest(
      classes = HistoricalSimulationEndpointTestApplication.class,
      webEnvironment = SpringBootTest.WebEnvironment.MOCK,
      properties = "opencop.dev.mode=false")
  @ActiveProfiles("it")
  class DevModeFalse extends AbstractWebIT {

    @Autowired private ApplicationContext applicationContext;

    @Test
    void generate_whenDevModeFalse_returnsNotFoundAndDoesNotLoadSimulationBeans() throws Exception {
      assertSimulationBeansNotLoaded(applicationContext);

      mockMvc
          .perform(
              post(ROUTE)
                  .with(csrf())
                  .with(authentication(TestAuthentications.oidcAdmin(USER_ID)))
                  .contentType("application/json")
                  .content("{}"))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  @SpringBootTest(
      classes = HistoricalSimulationEndpointTestApplication.class,
      webEnvironment = SpringBootTest.WebEnvironment.MOCK,
      properties = "spring.config.name=application-it")
  @ActiveProfiles("it")
  class DevModeMissing extends AbstractWebIT {

    @Autowired private ApplicationContext applicationContext;

    @Test
    void generate_whenDevModeMissing_returnsNotFoundAndDoesNotLoadSimulationBeans()
        throws Exception {
      assertSimulationBeansNotLoaded(applicationContext);

      mockMvc
          .perform(
              post(ROUTE)
                  .with(csrf())
                  .with(authentication(TestAuthentications.oidcAdmin(USER_ID)))
                  .contentType("application/json")
                  .content("{}"))
          .andExpect(status().isNotFound());
    }
  }

  private static void assertSimulationBeansNotLoaded(ApplicationContext applicationContext) {
    assertFalse(applicationContext.containsBeanDefinition("historicalSimulationEndpoint"));
    assertFalse(applicationContext.containsBeanDefinition("historicalSimulationService"));
    assertTrue(applicationContext.getBeansOfType(HistoricalSimulationEndpoint.class).isEmpty());
    assertTrue(applicationContext.getBeansOfType(HistoricalSimulationService.class).isEmpty());
  }
}
