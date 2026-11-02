// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.ports.usecase.media.MediaNotFoundException;
import com.opencop.core.api.general.ports.usecase.media.MediaView;
import com.opencop.core.api.general.ports.usecase.media.RetrieveMedia;
import com.opencop.core.api.general.technical.gateway.authorization.AuthorizationService;
import com.opencop.core.sdk.model.media.MediaAccess;
import com.opencop.core.sdk.model.media.MediaContentType;
import com.opencop.core.sdk.model.media.MediaRecord;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class RetrieveMediaEndpointTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID MEDIA_ID = UUID.randomUUID();
  private static final String SAS_URL =
      "http://localhost:10000/devstoreaccount1/opencop-common/blob";

  @Mock private RetrieveMedia usecase;
  private final AuthorizationService authz = new AuthorizationService();

  private RetrieveMediaEndpoint endpoint;

  @BeforeEach
  void setUp() {
    endpoint = new RetrieveMediaEndpoint(usecase, authz);
  }

  @Test
  void retrieve_withJsonAccept_returnsMediaView() throws MediaNotFoundException {
    var principal = principal(USER_ID);
    var view = new MediaView(media(MediaContentType.IMAGE), SAS_URL, List.of());
    when(usecase.getAsUser(MEDIA_ID, USER_ID)).thenReturn(view);

    var response = endpoint.retrieve(principal, MEDIA_ID, "application/json");

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(view, response.getBody());
    verify(usecase).getAsUser(MEDIA_ID, USER_ID);
  }

  @Test
  void retrieve_withHtmlAccept_redirectsToSasUrl() throws MediaNotFoundException {
    var principal = principal(USER_ID);
    when(usecase.getAsUser(MEDIA_ID, USER_ID))
        .thenReturn(new MediaView(media(MediaContentType.IMAGE), SAS_URL, List.of()));

    var response = endpoint.retrieve(principal, MEDIA_ID, "text/html,application/xhtml+xml");

    assertEquals(HttpStatus.FOUND, response.getStatusCode());
    assertEquals(URI.create(SAS_URL), response.getHeaders().getLocation());
  }

  @Test
  void retrieve_withImageAccept_forImageMedia_redirectsToSasUrl() throws MediaNotFoundException {
    var principal = principal(USER_ID);
    when(usecase.getAsUser(MEDIA_ID, USER_ID))
        .thenReturn(new MediaView(media(MediaContentType.IMAGE), SAS_URL, List.of()));

    var response = endpoint.retrieve(principal, MEDIA_ID, "image/webp,image/*,*/*;q=0.8");

    assertEquals(HttpStatus.FOUND, response.getStatusCode());
    assertEquals(URI.create(SAS_URL), response.getHeaders().getLocation());
  }

  @Test
  void retrieve_withImageAccept_forNonImageMedia_returnsJson() throws MediaNotFoundException {
    var principal = principal(USER_ID);
    var view = new MediaView(media(MediaContentType.OTHER), SAS_URL, List.of());
    when(usecase.getAsUser(MEDIA_ID, USER_ID)).thenReturn(view);

    var response = endpoint.retrieve(principal, MEDIA_ID, "image/*");

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(view, response.getBody());
  }

  @Test
  void retrieve_withAdminPrincipal_passesAdminFlagToUsecase() throws MediaNotFoundException {
    var principal = principal(USER_ID, false, true);
    var view = new MediaView(media(MediaContentType.IMAGE), SAS_URL, List.of());
    when(usecase.getWithoutAccessCheck(MEDIA_ID)).thenReturn(view);

    var response = endpoint.retrieve(principal, MEDIA_ID, "application/json");

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(view, response.getBody());
    verify(usecase).getWithoutAccessCheck(MEDIA_ID);
  }

  @Test
  void retrieve_withCurrentlyDemotedLoginAdmin_usesUserAccessCheck() throws MediaNotFoundException {
    var principal = principal(USER_ID, true, false);
    var view = new MediaView(media(MediaContentType.IMAGE), SAS_URL, List.of());
    when(usecase.getAsUser(MEDIA_ID, USER_ID)).thenReturn(view);

    var response = endpoint.retrieve(principal, MEDIA_ID, "application/json");

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(view, response.getBody());
    verify(usecase).getAsUser(MEDIA_ID, USER_ID);
    verify(usecase, never()).getWithoutAccessCheck(MEDIA_ID);
  }

  @Test
  void retrieve_whenMediaNotFoundThrows404() throws MediaNotFoundException {
    var principal = principal(USER_ID);
    when(usecase.getAsUser(MEDIA_ID, USER_ID)).thenThrow(new MediaNotFoundException(MEDIA_ID));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> endpoint.retrieve(principal, MEDIA_ID, "application/json"));

    assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    assertEquals("Media not found: " + MEDIA_ID, ex.getMessage());
  }

  private static MediaRecord media(MediaContentType contentType) {
    return new MediaRecord(
        MEDIA_ID,
        "media/2026/03/15/" + MEDIA_ID + "/photo",
        "photo",
        contentType,
        1024,
        MediaAccess.LOGGED_IN,
        USER_ID,
        USER_ID,
        Instant.now(),
        "image/jpeg",
        null,
        null,
        null);
  }

  private static OpenCopPrincipal principal(UUID userId) {
    return principal(userId, false, false);
  }

  private static OpenCopPrincipal principal(UUID userId, boolean loginAdmin, boolean currentAdmin) {
    return com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(userId, loginAdmin)
        .withRequestCurrentGlobalAdministrator(currentAdmin);
  }
}
