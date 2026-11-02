// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.connection.extension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.general.ports.usecase.connection.extension.ExtensionResult;
import com.opencop.core.api.general.ports.usecase.connection.extension.InvokeConnectionExtension;
import com.opencop.core.api.general.ports.usecase.connection.extension.InvokeConnectionExtensionCommand;
import com.opencop.core.api.general.ports.usecase.connection.extension.UnreadableExtensionBodyException;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExtensionMethod;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

class ConnectionExtensionEndpointTest {

  private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
  private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
  private static final String EXTENSION_ID = "data-sync";
  private static final String EXTENSION =
      "/v1/connections/" + SOURCE_ID + "/extensions/" + EXTENSION_ID;

  private final InvokeConnectionExtension usecase = mock(InvokeConnectionExtension.class);
  private final OpenCopPrincipal principal = mock(OpenCopPrincipal.class);
  private final ConnectionExtensionEndpoint endpoint =
      new ConnectionExtensionEndpoint(usecase, new ObjectMapper());

  @BeforeEach
  void givenAUser() {
    when(principal.internalUserId()).thenReturn(USER_ID);
    when(usecase.invoke(any())).thenReturn(ExtensionResult.none());
  }

  @Test
  void handsTheUseCaseTheCallerTheExtensionAndTheMethod() {
    var command = invoke(request("PUT", EXTENSION + "/items/alpha"));

    assertThat(command.sourceId()).isEqualTo(SOURCE_ID);
    assertThat(command.userId()).isEqualTo(USER_ID);
    assertThat(command.extensionId()).isEqualTo(EXTENSION_ID);
    assertThat(command.method()).isEqualTo(ExtensionMethod.PUT);
    assertThat(command.path()).containsExactly("items", "alpha");
  }

  @Test
  void theExtensionItselfHasAnEmptyPath() {
    assertThat(invoke(request("GET", EXTENSION)).path()).isEmpty();
  }

  /** Decoding each segment on its own keeps an encoded slash inside the variable it belongs to. */
  @Test
  void anEncodedSlashStaysInsideItsSegment() {
    assertThat(invoke(request("GET", EXTENSION + "/items/a%2Fb")).path())
        .containsExactly("items", "a/b");
  }

  @Test
  void eachSegmentIsDecoded() {
    assertThat(invoke(request("GET", EXTENSION + "/items/caf%C3%A9%20bar")).path())
        .containsExactly("items", "café bar");
  }

  /** A trailing slash names a different path, which no template with fewer segments matches. */
  @Test
  void aTrailingSlashEndsWithAnEmptySegment() {
    assertThat(invoke(request("GET", EXTENSION + "/items/alpha/")).path())
        .containsExactly("items", "alpha", "");
  }

  @Test
  void theOperationPathIsReadBelowTheContextPath() {
    var request = request("GET", "/api" + EXTENSION + "/items/alpha");
    request.setContextPath("/api");

    assertThat(invoke(request).path()).containsExactly("items", "alpha");
  }

  @Test
  void queryParametersAreDecodedAndAFlagWithoutValueIsEmpty() {
    var request = request("GET", EXTENSION + "/items");
    request.setQueryString("q=a%20b&tag=x&tag=y&flag");

    assertThat(invoke(request).query())
        .isEqualTo(Map.of("q", List.of("a b"), "tag", List.of("x", "y"), "flag", List.of("")));
  }

  /** As every other endpoint's request parameters: form-encoded. */
  @Test
  void aPlusInTheQueryIsASpaceAndAnEncodedPlusIsAPlus() {
    var request = request("GET", EXTENSION + "/items");
    request.setQueryString("q=a+b%20c&sum=1%2B1&a+key=v&empty=");

    assertThat(invoke(request).query())
        .isEqualTo(
            Map.of(
                "q", List.of("a b c"),
                "sum", List.of("1+1"),
                "a key", List.of("v"),
                "empty", List.of("")));
  }

  @Test
  void aJsonBodyIsReadAsTheOperationsType() {
    var request = request("PUT", EXTENSION + "/items/alpha");
    request.setContent("{\"role\":\"subscriber\"}".getBytes(StandardCharsets.UTF_8));

    assertThat(invoke(request).body().read(Subscribe.class)).isEqualTo(new Subscribe("subscriber"));
  }

  @Test
  void anEmptyBodyIsUnreadable() {
    var body = invoke(request("PUT", EXTENSION + "/items/alpha")).body();

    assertThatThrownBy(() -> body.read(Subscribe.class))
        .isInstanceOf(UnreadableExtensionBodyException.class);
  }

  @Test
  void aResultWithoutBodyAnswersNoContentAndOneWithABodyAnswersIt() {
    assertThat(
            endpoint
                .invoke(principal, SOURCE_ID, EXTENSION_ID, request("DELETE", EXTENSION + "/a"))
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    when(usecase.invoke(any())).thenReturn(ExtensionResult.of("alpha"));
    var answer = endpoint.invoke(principal, SOURCE_ID, EXTENSION_ID, request("GET", EXTENSION));

    assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(answer.getBody()).isEqualTo("\"alpha\"".getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Spring writes a string body as plain text whatever the content type, so a string result must
   * still reach the client as a JSON string, the way every other result is JSON.
   */
  @Test
  void aStringResultIsAnsweredAsAJsonString() throws Exception {
    when(usecase.invoke(any())).thenReturn(ExtensionResult.of("mission alpha"));
    var mockMvc =
        MockMvcBuilders.standaloneSetup(endpoint)
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    try {
      mockMvc
          .perform(get(EXTENSION + "/missions/alpha"))
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(content().string("\"mission alpha\""));
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  /**
   * Spring routes HEAD to a mapping that declares GET, but no extension operation answers it, so it
   * is refused rather than running the operation for a body nobody receives.
   */
  @Test
  void aHeadRequestIsNotAllowedAndReachesNoOperation() {
    var answer = endpoint.invoke(principal, SOURCE_ID, EXTENSION_ID, request("HEAD", EXTENSION));

    assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    verifyNoInteractions(usecase);
  }

  private InvokeConnectionExtensionCommand invoke(MockHttpServletRequest request) {
    endpoint.invoke(principal, SOURCE_ID, EXTENSION_ID, request);
    var command = ArgumentCaptor.forClass(InvokeConnectionExtensionCommand.class);
    verify(usecase).invoke(command.capture());
    return command.getValue();
  }

  private static MockHttpServletRequest request(String method, String uri) {
    return new MockHttpServletRequest(method, uri);
  }

  record Subscribe(String role) {}
}
