// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.scim;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.api.common.AppConfig;
import com.opencop.core.api.common.filter.RequestLoggingFilter;
import com.opencop.core.api.common.filter.RequestPathDebugFilter;
import com.opencop.core.api.common.scim.ScimClientConfig;
import com.opencop.core.api.general.business.usecase.user.ScimUserMutationUsecase;
import com.opencop.core.api.general.ports.gateway.repository.UserPreferenceRepository;
import com.opencop.core.api.general.technical.endpoint.scim.ProvisioningEndpoint;
import com.opencop.core.api.general.technical.gateway.scim.ScimGroupService;
import com.opencop.core.api.general.technical.gateway.scim.ScimUserService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(
    classes = ScimDiagnosticsComponentIT.ScimDiagnosticsTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "opencop.scim.bearer-token=test-scim-token",
      "opencop.scim.inbound-logging-enabled=true",
      "logging.level.com.opencop.core.api.common.scim.ScimInboundLoggingFilter=INFO",
      "logging.level.com.opencop.core.api.common.filter.RequestLoggingFilter=INFO",
      "logging.level.REQ_PATHS=INFO"
    })
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ExtendWith(OutputCaptureExtension.class)
@MockitoBean(
    types = {
      ScimUserService.class,
      ScimUserMutationUsecase.class,
      ScimGroupService.class,
      UserPreferenceRepository.class
    })
class ScimDiagnosticsComponentIT {

  private static final String PROVISIONING_CONFIG_PATH =
      "/scim/v1/provisioning/ServiceProviderConfig";
  private static final String ECHO_PATH = "/scim/test/echo";
  private static final String VALID_AUTHORIZATION = "Bearer test-scim-token";

  private final HttpClient httpClient = HttpClient.newHttpClient();
  private final URI provisioningConfigUri;
  private final URI echoUri;

  ScimDiagnosticsComponentIT(@LocalServerPort int port) {
    provisioningConfigUri = URI.create("http://localhost:" + port + PROVISIONING_CONFIG_PATH);
    echoUri = URI.create("http://localhost:" + port + ECHO_PATH);
  }

  @Test
  void diagnosticsEnabled_validBearerRetainsProvisioningSuccess(CapturedOutput output)
      throws Exception {
    var response = sendProvisioningRequest(VALID_AUTHORIZATION);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(this::assertScimJson);
    assertThat(response.body()).contains("authenticationSchemes");
    assertPathDiagnosticsRan(output);
    assertThat(output).contains("SCIM INBOUND GET " + PROVISIONING_CONFIG_PATH);
    assertThat(output).contains("Request Method: GET | Request URI: " + PROVISIONING_CONFIG_PATH);
  }

  @Test
  void diagnosticsEnabled_invalidBearerRemainsForbidden(CapturedOutput output) throws Exception {
    var response = sendProvisioningRequest("Bearer wrong-token");

    assertThat(response.statusCode()).isEqualTo(403);
    assertPathDiagnosticsRan(output);
    assertThat(output)
        .doesNotContain("SCIM INBOUND GET " + PROVISIONING_CONFIG_PATH)
        .doesNotContain("Request Method: GET | Request URI: " + PROVISIONING_CONFIG_PATH);
  }

  @Test
  void diagnosticsEnabled_validBearerPreservesMutationBodyWithoutInfoPayload(CapturedOutput output)
      throws Exception {
    var body = "authenticated-scim-body";

    var response = sendEchoRequest(VALID_AUTHORIZATION, body);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo(body);
    assertThat(output)
        .contains("URI=" + ECHO_PATH)
        .contains("SCIM INBOUND POST " + ECHO_PATH)
        .contains("Request Method: POST | Request URI: " + ECHO_PATH)
        .doesNotContain(body);
  }

  @Test
  void diagnosticsEnabled_invalidBearerRejectsBeforeMutationPayloadDiagnostics(
      CapturedOutput output) throws Exception {
    var body = "unauthenticated-scim-body";

    var response = sendEchoRequest("Bearer wrong-token", body);

    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(output)
        .contains("URI=" + ECHO_PATH)
        .doesNotContain("SCIM INBOUND POST " + ECHO_PATH)
        .doesNotContain("Request Method: POST | Request URI: " + ECHO_PATH)
        .doesNotContain(body);
  }

  private HttpResponse<String> sendProvisioningRequest(String authorization) throws Exception {
    var request =
        HttpRequest.newBuilder(provisioningConfigUri)
            .header("Accept", ProvisioningEndpoint.SCIM_JSON)
            .header("Authorization", authorization)
            .GET()
            .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> sendEchoRequest(String authorization, String body) throws Exception {
    var request =
        HttpRequest.newBuilder(echoUri)
            .header("Authorization", authorization)
            .header("Content-Type", MediaType.TEXT_PLAIN_VALUE)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private void assertScimJson(String contentType) {
    assertThat(contentType).startsWith(ProvisioningEndpoint.SCIM_JSON);
  }

  private static void assertPathDiagnosticsRan(CapturedOutput output) {
    assertThat(output).contains("URI=" + PROVISIONING_CONFIG_PATH);
  }

  @SpringBootConfiguration(proxyBeanMethods = false)
  @EnableAutoConfiguration(
      exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        FlywayAutoConfiguration.class
      })
  @Import({
    ScimClientConfig.class,
    RequestPathDebugFilter.class,
    ProvisioningEndpoint.class,
    ScimEchoEndpoint.class
  })
  static class ScimDiagnosticsTestApplication {

    @Bean
    FilterRegistrationBean<RequestLoggingFilter> loggingFilter() {
      return new AppConfig().loggingFilter();
    }
  }

  @RestController
  static class ScimEchoEndpoint {

    @PostMapping(
        path = ECHO_PATH,
        consumes = MediaType.TEXT_PLAIN_VALUE,
        produces = MediaType.TEXT_PLAIN_VALUE)
    String echo(@RequestBody String body) {
      return body;
    }
  }
}
