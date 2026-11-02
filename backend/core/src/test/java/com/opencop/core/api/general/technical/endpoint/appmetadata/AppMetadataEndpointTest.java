// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.appmetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.general.business.model.runtimefeature.RuntimeFeatureAvailability;
import com.opencop.core.api.general.business.model.runtimefeature.RuntimeFeatureMetadata;
import com.opencop.core.api.general.ports.usecase.runtimefeature.GetRuntimeFeatureMetadata;
import com.opencop.core.sdk.business.model.Roles;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureAudience;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureConsumerClass;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureState;
import com.opencop.core.sdk.technical.exception.GlobalExceptionManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AppMetadataEndpointTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655447001");

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(BuildMetadataConfiguration.class);

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    var buildMetadata =
        new BuildMetadata(
            "0.3.0", "abc123def456", "release/0.3.0-2026-07-02", "2026-07-03T12:00:00Z");
    mockMvc = mockMvcFor(buildMetadata, false);
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void nonAdmin_returnsDisabledWatchboxFeatureWithoutAdminNode() throws Exception {
    var auth = TestAuthentications.oidcUser(USER_ID);

    mockMvc
        .perform(get("/v1/app-metadata").with(securityContext(auth)))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.appVersion").value("0.3.0"))
        .andExpect(jsonPath("$.features.length()").value(2))
        .andExpect(jsonPath("$.features[0].name").value("rtsp"))
        .andExpect(jsonPath("$.features[0].enabled").value(true))
        .andExpect(jsonPath("$.features[1].name").value("watchbox"))
        .andExpect(jsonPath("$.features[1].enabled").value(false))
        .andExpect(jsonPath("$.admin").doesNotExist());
  }

  @Test
  void nonAdmin_returnsEnabledWatchboxFeatureWithoutAdminNode() throws Exception {
    var auth = TestAuthentications.oidcUser(USER_ID);

    mockMvcFor(
            new BuildMetadata(
                "0.3.0", "abc123def456", "release/0.3.0-2026-07-02", "2026-07-03T12:00:00Z"),
            true)
        .perform(get("/v1/app-metadata").with(securityContext(auth)))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.appVersion").value("0.3.0"))
        .andExpect(jsonPath("$.features.length()").value(2))
        .andExpect(jsonPath("$.features[1].name").value("watchbox"))
        .andExpect(jsonPath("$.features[1].enabled").value(true))
        .andExpect(jsonPath("$.admin").doesNotExist());
  }

  @Test
  void admin_returnsAppVersionFeatureAndAdminNode() throws Exception {
    var principal =
        com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(USER_ID, true);
    var auth =
        new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority(Roles.ADMIN)));

    mockMvc
        .perform(get("/v1/app-metadata").with(securityContext(auth)))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.appVersion").value("0.3.0"))
        .andExpect(jsonPath("$.features.length()").value(2))
        .andExpect(jsonPath("$.features[1].name").value("watchbox"))
        .andExpect(jsonPath("$.features[1].enabled").value(false))
        .andExpect(jsonPath("$.admin.bff.revision").value("abc123def456"))
        .andExpect(jsonPath("$.admin.bff.branch").value("release/0.3.0-2026-07-02"))
        .andExpect(jsonPath("$.admin.bff.buildDate").value("2026-07-03T12:00:00Z"))
        .andExpect(jsonPath("$.admin.runtimeFeatures.length()").value(2))
        .andExpect(jsonPath("$.admin.runtimeFeatures[0].name").value("rtsp"))
        .andExpect(jsonPath("$.admin.runtimeFeatures[1].name").value("watchbox"));
  }

  @Test
  void unsetAppVersion_omitsAppVersion() throws Exception {
    var buildMetadata = new BuildMetadata(null, null, null, null);
    var auth = TestAuthentications.oidcUser(USER_ID);

    mockMvcFor(buildMetadata, false)
        .perform(get("/v1/app-metadata").with(securityContext(auth)))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.appVersion").doesNotExist())
        .andExpect(jsonPath("$.features.length()").value(2))
        .andExpect(jsonPath("$.features[1].name").value("watchbox"))
        .andExpect(jsonPath("$.features[1].enabled").value(false))
        .andExpect(jsonPath("$.admin").doesNotExist());
  }

  @Test
  void unsetBranch_returnsNullBranch() throws Exception {
    var buildMetadata =
        new BuildMetadataConfiguration()
            .buildMetadata(
                new BuildMetadataProperties("0.3.0", "abc123def456", "", "2026-07-03T12:00:00Z"));
    var principal =
        com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(USER_ID, true);
    var auth =
        new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority(Roles.ADMIN)));

    mockMvcFor(buildMetadata, false)
        .perform(get("/v1/app-metadata").with(securityContext(auth)))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.appVersion").value("0.3.0"))
        .andExpect(jsonPath("$.features.length()").value(2))
        .andExpect(jsonPath("$.features[1].name").value("watchbox"))
        .andExpect(jsonPath("$.features[1].enabled").value(false))
        .andExpect(jsonPath("$.admin.bff.revision").value("abc123def456"))
        .andExpect(jsonPath("$.admin.bff.branch").doesNotExist())
        .andExpect(jsonPath("$.admin.bff.buildDate").value("2026-07-03T12:00:00Z"));
  }

  @Test
  void configuration_normalizesUnknownRevisionToUnset() throws Exception {
    var buildMetadata =
        new BuildMetadataConfiguration()
            .buildMetadata(
                new BuildMetadataProperties(
                    "0.3.0", "unknown", "release/0.3.0", "2026-07-03T12:00:00Z"));
    var principal =
        com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(USER_ID, true);
    var auth =
        new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority(Roles.ADMIN)));

    mockMvcFor(buildMetadata, false)
        .perform(get("/v1/app-metadata").with(securityContext(auth)))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.appVersion").value("0.3.0"))
        .andExpect(jsonPath("$.features.length()").value(2))
        .andExpect(jsonPath("$.features[1].name").value("watchbox"))
        .andExpect(jsonPath("$.features[1].enabled").value(false))
        .andExpect(jsonPath("$.admin.bff.revision").doesNotExist())
        .andExpect(jsonPath("$.admin.bff.branch").value("release/0.3.0"))
        .andExpect(jsonPath("$.admin.bff.buildDate").value("2026-07-03T12:00:00Z"));
  }

  @Test
  void configuration_bindsBuildProperties() {
    contextRunner
        .withPropertyValues(
            "opencop.build.app-version=0.0.0",
            "opencop.build.revision=abc123def456",
            "opencop.build.branch=release/0.0.0-2000-00-00",
            "opencop.build.date=2000-01-01T00:00:00Z")
        .run(
            context -> {
              assertThat(context).hasSingleBean(BuildMetadataProperties.class);
              assertThat(context.getBean(BuildMetadata.class))
                  .isEqualTo(
                      new BuildMetadata(
                          "0.0.0",
                          "abc123def456",
                          "release/0.0.0-2000-00-00",
                          "2000-01-01T00:00:00Z"));
            });
  }

  private MockMvc mockMvcFor(BuildMetadata buildMetadata, boolean watchboxEnabled) {
    GetRuntimeFeatureMetadata metadataUsecase =
        (callerUserId, includeAdminState) -> {
          var states =
              List.of(
                  new RuntimeFeatureState(
                      "rtsp",
                      RuntimeFeatureConsumerClass.FRONTEND_ONLY,
                      new RuntimeFeatureAudience.All(),
                      0,
                      Instant.EPOCH),
                  new RuntimeFeatureState(
                      "watchbox",
                      RuntimeFeatureConsumerClass.BACKEND_ENFORCED,
                      watchboxEnabled
                          ? new RuntimeFeatureAudience.All()
                          : new RuntimeFeatureAudience.None(),
                      0,
                      Instant.EPOCH));
          return new RuntimeFeatureMetadata(
              List.of(
                  new RuntimeFeatureAvailability("rtsp", true),
                  new RuntimeFeatureAvailability("watchbox", watchboxEnabled)),
              includeAdminState ? Optional.of(states) : Optional.empty());
        };
    return MockMvcBuilders.standaloneSetup(new AppMetadataEndpoint(buildMetadata, metadataUsecase))
        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
        .setControllerAdvice(new GlobalExceptionManager())
        .build();
  }

  private RequestPostProcessor securityContext(Authentication auth) {
    return request -> {
      var context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(auth);
      SecurityContextHolder.setContext(context);
      return request;
    };
  }
}
