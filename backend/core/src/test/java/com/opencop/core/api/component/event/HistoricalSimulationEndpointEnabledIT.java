// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    classes = HistoricalSimulationEndpointTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "opencop.dev.mode=true")
@ActiveProfiles("it")
class HistoricalSimulationEndpointEnabledIT extends AbstractWebIT {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440301");

  @MockitoBean(name = "rawEventProducer")
  private MessageBrokerProducer<byte[]> rawEventProducer;

  @Test
  void generate_whenDevModeTrue_registersRouteAndGeneratesData() throws Exception {
    mockMvc
        .perform(
            post("/v1/history/test-data/generate")
                .with(csrf())
                .with(authentication(TestAuthentications.oidcAdmin(USER_ID)))
                .contentType("application/json")
                .content(
                    """
                    {
                      "windowStart": "2026-04-06T10:00:00Z",
                      "windowDuration": "PT1M",
                      "chunkDuration": "PT1M",
                      "tickDuration": "PT1M",
                      "uniquePoints": 1,
                      "randomSeeded": true,
                      "seed": 42
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalMessagesPublished").value(1))
        .andExpect(jsonPath("$.entityCount").value(1));

    verify(rawEventProducer).send(any(byte[].class), any());
  }
}
