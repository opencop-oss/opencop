// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.support;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.opencop.core.sdk.component.database.AbstractDatabaseIT;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Base for HTTP-facing component tests. */
public abstract class AbstractWebIT extends AbstractDatabaseIT {

  @Autowired protected WebApplicationContext context;
  @Autowired protected ObjectMapper objectMapper;

  protected MockMvc mockMvc;

  @BeforeEach
  void setUpMockMvc() {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  protected <T> T readResponse(MvcResult result, Class<T> type) throws Exception {
    return objectMapper.readValue(result.getResponse().getContentAsByteArray(), type);
  }

  protected <T> T readResponse(MvcResult result, TypeReference<T> type) throws Exception {
    return objectMapper.readValue(result.getResponse().getContentAsByteArray(), type);
  }

  protected UUID uuidFromLocation(MvcResult result) {
    var location = result.getResponse().getHeader("Location");
    if (location == null) {
      throw new IllegalStateException("Missing Location header");
    }
    return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
  }
}
