// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;

class CustomAuthenticationSuccessHandlerTest {

  private static final URI ALLOWED_DOMAIN = URI.create("https://opencop.test");

  @Test
  void redirectsToValidatedSameOriginTargetAndRemovesTheTemporaryAttribute() throws Exception {
    var authentication = mock(Authentication.class);
    var request = new MockHttpServletRequest();
    request.getSession().setAttribute("validated_redirect_uri", "https://opencop.test/map?id=7");
    var response = new MockHttpServletResponse();

    handler().onAuthenticationSuccess(request, response, authentication);

    assertThat(response.getRedirectedUrl()).isEqualTo("https://opencop.test/map?id=7");
    assertThat(request.getSession().getAttribute("validated_redirect_uri")).isNull();
    verifyNoInteractions(authentication);
  }

  @Test
  void fallsBackToLoginSuccessForARejectedRedirectTarget() throws Exception {
    var request = new MockHttpServletRequest();
    request.getSession().setAttribute("validated_redirect_uri", "https://attacker.test/map");
    var response = new MockHttpServletResponse();

    handler().onAuthenticationSuccess(request, response, mock(Authentication.class));

    assertThat(response.getRedirectedUrl()).isEqualTo("https://opencop.test/login_success");
    assertThat(request.getSession().getAttribute("validated_redirect_uri")).isNull();
  }

  @Test
  void fallsBackToLoginSuccessForAMalformedRedirectTarget() throws Exception {
    var request = new MockHttpServletRequest();
    request.getSession().setAttribute("validated_redirect_uri", "not a valid URI");
    var response = new MockHttpServletResponse();

    handler().onAuthenticationSuccess(request, response, mock(Authentication.class));

    assertThat(response.getRedirectedUrl()).isEqualTo("https://opencop.test/login_success");
    assertThat(request.getSession().getAttribute("validated_redirect_uri")).isNull();
  }

  private static CustomAuthenticationSuccessHandler handler() {
    return new CustomAuthenticationSuccessHandler(ALLOWED_DOMAIN);
  }
}
