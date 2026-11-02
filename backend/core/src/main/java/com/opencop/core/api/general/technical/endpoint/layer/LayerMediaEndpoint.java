// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.layer;

import com.opencop.core.api.general.ports.gateway.media.LayerMediaRepository;
import com.opencop.core.api.general.ports.usecase.media.MediaNotFoundException;
import com.opencop.core.api.general.ports.usecase.media.MediaView;
import com.opencop.core.api.general.ports.usecase.media.RetrieveMedia;
import com.opencop.core.api.general.ports.usecase.media.ServeLayerIcon;
import com.opencop.core.api.general.technical.service.LayerAuthorizationService;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Layer-scoped media endpoint. Checks that the requesting user is a member of the layer before
 * serving media. Supports two response modes:
 *
 * <ul>
 *   <li>{@code Accept: application/json} — returns JSON {@code MediaView} with SAS URL
 *   <li>Any other Accept — returns raw image bytes with correct Content-Type (Caffeine-cached)
 * </ul>
 */
@RestController
@RequestMapping("/v1/layers")
public class LayerMediaEndpoint {

  private final LayerAuthorizationService layerAuth;
  private final RetrieveMedia retrieveMedia;
  private final ServeLayerIcon serveLayerIcon;
  private final LayerMediaRepository layerMediaRepository;

  LayerMediaEndpoint(
      LayerAuthorizationService layerAuth,
      RetrieveMedia retrieveMedia,
      ServeLayerIcon serveLayerIcon,
      LayerMediaRepository layerMediaRepository) {
    this.layerAuth = layerAuth;
    this.retrieveMedia = retrieveMedia;
    this.serveLayerIcon = serveLayerIcon;
    this.layerMediaRepository = layerMediaRepository;
  }

  @GetMapping("/{layerId}/media/{mediaId}")
  public ResponseEntity<Object> getMedia(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @PathVariable UUID layerId,
      @PathVariable UUID mediaId,
      @RequestHeader(value = "Accept", defaultValue = "") String accept) {
    layerAuth.assertLayerMember(layerId, authentication);

    // IDOR prevention: verify the media actually belongs to this layer, not just that the user
    // is a layer member. Without this, any layer member could access any media by guessing IDs.
    // See:
    // https://cheatsheetseries.owasp.org/cheatsheets/Insecure_Direct_Object_Reference_Prevention_Cheat_Sheet.html
    if (!layerMediaRepository.exists(layerId, mediaId)) {
      throw new GlobalOpenCopException("Media not found", HttpStatus.NOT_FOUND);
    }

    if (wantsJson(accept)) {
      // Layer membership grants access — bypass MediaRecord access checks.
      var view = retrieveLayerMedia(mediaId);
      return ResponseEntity.ok((Object) view);
    }

    // Serve raw icon bytes (Caffeine-cached)
    var icon = serveLayerIcon.get(mediaId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(icon.mimeType()))
        .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
        .body((Object) icon.bytes());
  }

  private static boolean wantsJson(String accept) {
    var normalized = accept.toLowerCase(Locale.ROOT);
    return normalized.contains("application/json");
  }

  private MediaView retrieveLayerMedia(UUID mediaId) {
    try {
      return retrieveMedia.getWithoutAccessCheck(mediaId);
    } catch (MediaNotFoundException _) {
      throw new GlobalOpenCopException("Media not found", HttpStatus.NOT_FOUND);
    }
  }
}
