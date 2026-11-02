// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.media;

import com.opencop.core.api.general.ports.usecase.media.MediaNotFoundException;
import com.opencop.core.api.general.ports.usecase.media.MediaView;
import com.opencop.core.api.general.ports.usecase.media.RetrieveMedia;
import com.opencop.core.api.general.technical.gateway.authorization.AuthorizationService;
import com.opencop.core.sdk.model.media.MediaContentType;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.net.URI;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RetrieveMediaEndpoint {

  private final RetrieveMedia usecase;
  private final AuthorizationService authz;

  RetrieveMediaEndpoint(RetrieveMedia usecase, AuthorizationService authz) {
    this.usecase = usecase;
    this.authz = authz;
  }

  // Content negotiation:
  // - browser address bar sends "Accept: text/html,..." → 302 redirect to the SAS URL
  // - image requests for IMAGE media (e.g. "Accept: image/webp,image/*,...") → 302 redirect
  // - API clients (fetch with Accept: */* or application/json) → JSON MediaView
  // This lets you test access levels by pasting the URL in a browser logged in as different
  // users, without needing dedicated UI.
  @GetMapping("/v1/media/{mediaId}")
  public ResponseEntity<Object> retrieve(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @PathVariable UUID mediaId,
      @RequestHeader(value = "Accept", defaultValue = "") String accept) {
    var isAdmin = authz.isAdmin(authentication);
    var view = retrieveMedia(mediaId, isAdmin, authentication.internalUserId());

    if (wantsRedirect(accept, view.record().contentType())) {
      return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(view.url())).build();
    }
    return ResponseEntity.ok((Object) view);
  }

  private static boolean wantsRedirect(String accept, MediaContentType contentType) {
    var normalizedAccept = accept.toLowerCase(Locale.ROOT);
    return normalizedAccept.contains("text/html")
        || (contentType == MediaContentType.IMAGE && normalizedAccept.contains("image/"));
  }

  private MediaView retrieveMedia(UUID mediaId, boolean isAdmin, UUID userId) {
    try {
      return isAdmin ? usecase.getWithoutAccessCheck(mediaId) : usecase.getAsUser(mediaId, userId);
    } catch (MediaNotFoundException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.NOT_FOUND);
    }
  }
}
