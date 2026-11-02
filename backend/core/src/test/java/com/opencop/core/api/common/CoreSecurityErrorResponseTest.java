// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

class CoreSecurityErrorResponseTest {

  @Test
  void unauthorizedOwnsTheExactMvcAndServletContract() throws IOException {
    assertSharedContract(
        CoreSecurityErrorResponse.UNAUTHORIZED,
        HttpStatus.UNAUTHORIZED,
        "{\"error\":\"unauthorized\",\"message\":\"Login required\",\"loginUrl\":\"/oauth2/authorization/oidc\"}",
        "no-store");
  }

  @Test
  void forbiddenOwnsTheExactMvcAndServletContract() throws IOException {
    assertSharedContract(
        CoreSecurityErrorResponse.FORBIDDEN,
        HttpStatus.FORBIDDEN,
        "{\"error\":\"forbidden\"}",
        null);
  }

  @Test
  void csrfRejectedOwnsTheExactMvcAndServletContract() throws IOException {
    assertSharedContract(
        CoreSecurityErrorResponse.valueOf("CSRF_REJECTED"),
        HttpStatus.FORBIDDEN,
        "{\"error\":\"forbidden\",\"code\":\"CSRF_REJECTED\"}",
        null);
  }

  private static void assertSharedContract(
      CoreSecurityErrorResponse contract,
      HttpStatus expectedStatus,
      String expectedBody,
      String expectedCacheControl)
      throws IOException {
    var mvcResponse = contract.responseEntity();
    var servletResponse = new MockHttpServletResponse();

    contract.writeTo(servletResponse);

    assertEquals(expectedStatus, mvcResponse.getStatusCode());
    assertEquals(MediaType.APPLICATION_JSON, mvcResponse.getHeaders().getContentType());
    assertEquals(expectedBody, mvcResponse.getBody());
    assertEquals(
        expectedCacheControl, mvcResponse.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
    assertEquals(expectedStatus.value(), servletResponse.getStatus());
    assertEquals(MediaType.APPLICATION_JSON_VALUE, servletResponse.getContentType());
    assertEquals(expectedBody, servletResponse.getContentAsString());
    if (expectedCacheControl == null) {
      assertNull(servletResponse.getHeader(HttpHeaders.CACHE_CONTROL));
    } else {
      assertEquals(expectedCacheControl, servletResponse.getHeader(HttpHeaders.CACHE_CONTROL));
    }
  }
}
