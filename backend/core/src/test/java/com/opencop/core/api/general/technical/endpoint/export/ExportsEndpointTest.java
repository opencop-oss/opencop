// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.general.ports.usecase.export.DownloadExport;
import com.opencop.core.api.general.ports.usecase.export.DownloadExport.DownloadRedirect;
import com.opencop.core.api.general.ports.usecase.export.ExportFeatures;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.ports.exception.GlobalErrorResponse;
import com.opencop.core.sdk.technical.exception.GlobalExceptionManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

class ExportsEndpointTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655447001");
  private static final UUID SUBJECT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655447002");

  private ExportFeatures exportFeatures;
  private DownloadExport downloadExport;
  private ObjectMapper objectMapper;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    exportFeatures = mock(ExportFeatures.class);
    downloadExport = mock(DownloadExport.class);
    objectMapper = new ObjectMapper();
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ExportsEndpoint(exportFeatures, downloadExport))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new GlobalExceptionManager())
            .build();
  }

  @Test
  void create_whenFormatIsUnsupported_returnsBadRequestBeforeUsecase() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/v1/exports")
                    .with(csrf())
                    .with(authentication(TestAuthentications.oidcUser(USER_ID)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"format":"KML","subjects":[{"subjectType":"LAYER","subjectId":"%s"}]}
                        """
                            .formatted(SUBJECT_ID)))
            .andExpect(status().isBadRequest())
            .andReturn();

    var error =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), GlobalErrorResponse.class);
    assertEquals("Invalid request body", error.message());
    verifyNoInteractions(exportFeatures, downloadExport);
  }

  @Test
  void download_whenAuthorized_redirectsToSignedUrlWithoutResponseBody() throws Exception {
    var exportId = UUID.fromString("550e8400-e29b-41d4-a716-446655447003");
    var signedUrl = "https://storage.example/exports/source-export.csv?sig=abc";
    when(downloadExport.download(any(), eq(exportId)))
        .thenReturn(
            new DownloadRedirect("source-export.csv", new MediaType("text", "csv"), signedUrl));

    var result =
        mockMvc
            .perform(
                get("/v1/exports/{exportId}/download", exportId)
                    .with(authentication(TestAuthentications.oidcUser(USER_ID))))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", signedUrl))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn();

    assertEquals("", result.getResponse().getContentAsString());
  }
}
