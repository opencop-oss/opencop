// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.user;

import com.opencop.core.api.general.business.model.user.UserPreference;
import com.opencop.core.api.general.business.model.user.UserPreferencePatch;
import com.opencop.core.api.general.business.usecase.user.UserPreferenceUsecase;
import com.opencop.core.api.general.ports.gateway.repository.UserPreferenceRepository;
import com.opencop.core.api.general.ports.usecase.media.DeleteMedia;
import com.opencop.core.api.general.ports.usecase.media.RetrieveMedia;
import com.opencop.core.api.general.ports.usecase.media.UploadMedia;
import com.opencop.core.api.general.technical.model.AvatarUploadRequest;
import com.opencop.core.sdk.model.media.MediaAccess;
import com.opencop.core.sdk.model.media.MediaUsage;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class AvatarEndpoint {

  private static final Logger log = LoggerFactory.getLogger(AvatarEndpoint.class);

  private final UploadMedia uploadMedia;
  private final DeleteMedia deleteMedia;
  private final RetrieveMedia retrieveMedia;
  private final UserPreferenceRepository preferenceRepository;
  private final UserPreferenceUsecase preferences;

  AvatarEndpoint(
      UploadMedia uploadMedia,
      DeleteMedia deleteMedia,
      RetrieveMedia retrieveMedia,
      UserPreferenceRepository preferenceRepository,
      UserPreferenceUsecase preferences) {
    this.uploadMedia = uploadMedia;
    this.deleteMedia = deleteMedia;
    this.retrieveMedia = retrieveMedia;
    this.preferenceRepository = preferenceRepository;
    this.preferences = preferences;
  }

  @PostMapping(value = "/v1/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<UserPreference> uploadAvatar(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @RequestPart(name = "body", required = false) AvatarUploadRequest request,
      @RequestPart("original") MultipartFile original,
      @RequestPart(name = "thumbnails", required = false) List<MultipartFile> thumbnails)
      throws IOException, PersistenceException {

    var userId = authentication.internalUserId();

    // 1. Upload original (access=LOGGED_IN so other users can see the avatar)
    var originalRecord =
        uploadMedia.upload(
            userId,
            original.getOriginalFilename(),
            original.getContentType(),
            original.getSize(),
            MediaAccess.LOGGED_IN,
            original.getInputStream(),
            null,
            MediaUsage.AVATAR);

    // 2. Upload thumbnails linked to the original
    if (thumbnails != null) {
      for (var thumb : thumbnails) {
        uploadMedia.upload(
            userId,
            thumb.getOriginalFilename(),
            thumb.getContentType(),
            thumb.getSize(),
            MediaAccess.LOGGED_IN,
            thumb.getInputStream(),
            originalRecord.id(),
            MediaUsage.AVATAR);
      }
    }

    // 3. Check for existing avatar before updating
    var currentPref = preferenceRepository.findByUserId(userId);
    var oldAvatarId = currentPref.avatarMediaId();

    // 4. Point preference to new avatar
    var updatedPref =
        preferenceRepository.patch(userId, UserPreferencePatch.avatarOnly(originalRecord.id()));

    // 5. Delete old avatar media (original + thumbnails) if it existed
    if (oldAvatarId != null) {
      deleteOldAvatar(oldAvatarId, userId);
    }

    return ResponseEntity.ok(forResponse(updatedPref, authentication));
  }

  @DeleteMapping("/v1/me/avatar")
  public ResponseEntity<UserPreference> deleteAvatar(
      @AuthenticationPrincipal OpenCopPrincipal authentication) throws PersistenceException {

    var userId = authentication.internalUserId();
    var currentPref = preferenceRepository.findByUserId(userId);
    var oldAvatarId = currentPref.avatarMediaId();

    // Clear the avatar reference
    var updatedPref = preferenceRepository.patch(userId, UserPreferencePatch.avatarOnly(null));

    // Delete old avatar media if it existed
    if (oldAvatarId != null) {
      deleteOldAvatar(oldAvatarId, userId);
    }

    return ResponseEntity.ok(forResponse(updatedPref, authentication));
  }

  private void deleteOldAvatar(UUID mediaId, UUID userId) {
    try {
      // Retrieve to find thumbnails, then delete all
      var view = retrieveMedia.getAsUser(mediaId, userId);
      for (var thumb : view.thumbnails()) {
        try {
          deleteMedia.delete(thumb.id());
        } catch (Exception e) {
          log.warn("Failed to delete avatar thumbnail {}: {}", thumb.id(), e.getMessage());
        }
      }
      deleteMedia.delete(mediaId);
    } catch (Exception e) {
      log.warn("Failed to delete old avatar media {}: {}", mediaId, e.getMessage());
    }
  }

  private UserPreference forResponse(UserPreference pref, OpenCopPrincipal authentication) {
    return preferences.forResponse(pref, authentication);
  }
}
