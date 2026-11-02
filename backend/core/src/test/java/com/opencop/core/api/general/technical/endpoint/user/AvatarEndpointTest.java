// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.user.UserPreference;
import com.opencop.core.api.general.business.model.user.UserPreferencePatch;
import com.opencop.core.api.general.business.usecase.map.MapTileAccessUsecase;
import com.opencop.core.api.general.business.usecase.user.UserPreferenceUsecase;
import com.opencop.core.api.general.ports.gateway.repository.UserPreferenceRepository;
import com.opencop.core.api.general.ports.usecase.media.DeleteMedia;
import com.opencop.core.api.general.ports.usecase.media.MediaView;
import com.opencop.core.api.general.ports.usecase.media.RetrieveMedia;
import com.opencop.core.api.general.ports.usecase.media.ThumbnailRef;
import com.opencop.core.api.general.ports.usecase.media.UploadMedia;
import com.opencop.core.api.general.technical.model.AvatarUploadRequest;
import com.opencop.core.sdk.business.model.MeasurementSystem;
import com.opencop.core.sdk.business.model.TemperatureUnit;
import com.opencop.core.sdk.business.model.ZoomBehavior;
import com.opencop.core.sdk.business.model.map.tile.MapTile;
import com.opencop.core.sdk.model.media.MediaAccess;
import com.opencop.core.sdk.model.media.MediaContentType;
import com.opencop.core.sdk.model.media.MediaRecord;
import com.opencop.core.sdk.model.media.MediaUsage;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class AvatarEndpointTest {

  @Mock private UploadMedia uploadMedia;
  @Mock private DeleteMedia deleteMedia;
  @Mock private RetrieveMedia retrieveMedia;
  @Mock private UserPreferenceRepository preferenceRepository;
  @Mock private MapTileAccessUsecase tileAccess;

  private AvatarEndpoint endpoint;

  private static final UUID USER_ID = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    // A real preference usecase over the mocked gateways: the assertions below cover the
    // unusable-default-tile rule it applies, which a mocked usecase would not exercise.
    var preferences = new UserPreferenceUsecase(preferenceRepository, retrieveMedia, tileAccess);
    endpoint =
        new AvatarEndpoint(
            uploadMedia, deleteMedia, retrieveMedia, preferenceRepository, preferences);
  }

  @Test
  void uploadAvatar_uploadsOriginalAndUpdatesPref() throws Exception {
    var originalId = UUID.randomUUID();
    var originalRecord = mediaRecord(originalId, "photo.jpg", "image/jpeg");
    var originalFile = mockMultipartFile("photo.jpg", "image/jpeg", new byte[] {1, 2, 3});

    when(uploadMedia.upload(
            eq(USER_ID),
            eq("photo.jpg"),
            eq("image/jpeg"),
            eq(3L),
            eq(MediaAccess.LOGGED_IN),
            any(),
            isNull(),
            eq(MediaUsage.AVATAR)))
        .thenReturn(originalRecord);

    var prefWithoutAvatar = pref(null);
    var prefWithAvatar = pref(originalId);
    when(preferenceRepository.findByUserId(USER_ID)).thenReturn(prefWithoutAvatar);
    when(preferenceRepository.patch(USER_ID, UserPreferencePatch.avatarOnly(originalId)))
        .thenReturn(prefWithAvatar);

    var avatarView = new MediaView(originalRecord, "https://blob/photo.jpg", List.of());
    when(retrieveMedia.getAsUser(originalId, USER_ID)).thenReturn(avatarView);

    var response =
        endpoint.uploadAvatar(principal(), new AvatarUploadRequest(), originalFile, null);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(originalId, response.getBody().avatarMediaId());
    verify(preferenceRepository).patch(USER_ID, UserPreferencePatch.avatarOnly(originalId));
    verify(deleteMedia, never()).delete(any());
  }

  @Test
  void uploadAvatar_withThumbnails_uploadsAll() throws Exception {
    var originalId = UUID.randomUUID();
    var originalRecord = mediaRecord(originalId, "photo.jpg", "image/jpeg");
    var originalFile = mockMultipartFile("photo.jpg", "image/jpeg", new byte[] {1, 2, 3});
    var thumbFile = mockMultipartFile("thumb.webp", "image/webp", new byte[] {4, 5});
    var thumbRecord = mediaRecord(UUID.randomUUID(), "thumb.webp", "image/webp");

    when(uploadMedia.upload(
            eq(USER_ID),
            eq("photo.jpg"),
            eq("image/jpeg"),
            eq(3L),
            eq(MediaAccess.LOGGED_IN),
            any(),
            isNull(),
            eq(MediaUsage.AVATAR)))
        .thenReturn(originalRecord);
    when(uploadMedia.upload(
            eq(USER_ID),
            eq("thumb.webp"),
            eq("image/webp"),
            eq(2L),
            eq(MediaAccess.LOGGED_IN),
            any(),
            eq(originalId),
            eq(MediaUsage.AVATAR)))
        .thenReturn(thumbRecord);

    var prefWithoutAvatar = pref(null);
    var prefWithAvatar = pref(originalId);
    when(preferenceRepository.findByUserId(USER_ID)).thenReturn(prefWithoutAvatar);
    when(preferenceRepository.patch(USER_ID, UserPreferencePatch.avatarOnly(originalId)))
        .thenReturn(prefWithAvatar);

    var avatarView = new MediaView(originalRecord, "https://blob/photo.jpg", List.of());
    when(retrieveMedia.getAsUser(originalId, USER_ID)).thenReturn(avatarView);

    var response =
        endpoint.uploadAvatar(
            principal(), new AvatarUploadRequest(), originalFile, List.of(thumbFile));

    assertEquals(HttpStatus.OK, response.getStatusCode());
    // Verify both original and thumbnail were uploaded
    verify(uploadMedia)
        .upload(
            eq(USER_ID),
            eq("photo.jpg"),
            eq("image/jpeg"),
            eq(3L),
            eq(MediaAccess.LOGGED_IN),
            any(),
            isNull(),
            eq(MediaUsage.AVATAR));
    verify(uploadMedia)
        .upload(
            eq(USER_ID),
            eq("thumb.webp"),
            eq("image/webp"),
            eq(2L),
            eq(MediaAccess.LOGGED_IN),
            any(),
            eq(originalId),
            eq(MediaUsage.AVATAR));
  }

  @Test
  void uploadAvatar_replacesExistingAvatar() throws Exception {
    var oldAvatarId = UUID.randomUUID();
    var oldThumbId = UUID.randomUUID();
    var newOriginalId = UUID.randomUUID();
    var newRecord = mediaRecord(newOriginalId, "new.jpg", "image/jpeg");
    var originalFile = mockMultipartFile("new.jpg", "image/jpeg", new byte[] {1});

    when(uploadMedia.upload(
            eq(USER_ID),
            eq("new.jpg"),
            eq("image/jpeg"),
            eq(1L),
            eq(MediaAccess.LOGGED_IN),
            any(),
            isNull(),
            eq(MediaUsage.AVATAR)))
        .thenReturn(newRecord);

    // Existing avatar with a thumbnail
    var prefWithOldAvatar = pref(oldAvatarId);
    var prefWithNewAvatar = pref(newOriginalId);
    when(preferenceRepository.findByUserId(USER_ID)).thenReturn(prefWithOldAvatar);
    when(preferenceRepository.patch(USER_ID, UserPreferencePatch.avatarOnly(newOriginalId)))
        .thenReturn(prefWithNewAvatar);

    var oldAvatarView =
        new MediaView(
            mediaRecord(oldAvatarId, "old.jpg", "image/jpeg"),
            "https://blob/old.jpg",
            List.of(new ThumbnailRef(oldThumbId, "https://blob/thumb.webp", 64, 64, "image/webp")));
    when(retrieveMedia.getAsUser(oldAvatarId, USER_ID)).thenReturn(oldAvatarView);

    var newAvatarView = new MediaView(newRecord, "https://blob/new.jpg", List.of());
    when(retrieveMedia.getAsUser(newOriginalId, USER_ID)).thenReturn(newAvatarView);

    var response =
        endpoint.uploadAvatar(principal(), new AvatarUploadRequest(), originalFile, null);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    // Verify old avatar thumbnail + original were deleted
    verify(deleteMedia).delete(oldThumbId);
    verify(deleteMedia).delete(oldAvatarId);
  }

  @Test
  void deleteAvatar_clearsPreferenceAndDeletesMedia() throws Exception {
    var avatarId = UUID.randomUUID();
    var thumbId = UUID.randomUUID();

    var prefWithAvatar = pref(avatarId);
    var prefCleared = pref(null);
    when(preferenceRepository.findByUserId(USER_ID)).thenReturn(prefWithAvatar);
    when(preferenceRepository.patch(USER_ID, UserPreferencePatch.avatarOnly(null)))
        .thenReturn(prefCleared);

    var avatarView =
        new MediaView(
            mediaRecord(avatarId, "photo.jpg", "image/jpeg"),
            "https://blob/photo.jpg",
            List.of(new ThumbnailRef(thumbId, "https://blob/thumb.webp", 64, 64, "image/webp")));
    when(retrieveMedia.getAsUser(avatarId, USER_ID)).thenReturn(avatarView);

    var response = endpoint.deleteAvatar(principal());

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNull(response.getBody().avatarMediaId());
    verify(preferenceRepository).patch(USER_ID, UserPreferencePatch.avatarOnly(null));
    verify(deleteMedia).delete(thumbId);
    verify(deleteMedia).delete(avatarId);
  }

  @Test
  void deleteAvatar_whenNoAvatarSet_returnsOkWithoutDeleting() throws Exception {
    var prefNoAvatar = pref(null);
    when(preferenceRepository.findByUserId(USER_ID)).thenReturn(prefNoAvatar);
    when(preferenceRepository.patch(USER_ID, UserPreferencePatch.avatarOnly(null)))
        .thenReturn(prefNoAvatar);

    var response = endpoint.deleteAvatar(principal());

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNull(response.getBody().avatarMediaId());
    verify(deleteMedia, never()).delete(any());
  }

  private static OpenCopPrincipal principal() {
    return com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(USER_ID);
  }

  /**
   * The avatar responses carry the stored preference, so they share the tile re-check with /v1/me
   * and the preference routes. Without it a former viewer keeps receiving the tile.
   */
  @Test
  void deleteAvatar_dropsADefaultTileTheUserCanNoLongerUse() throws Exception {
    var tile = new MapTile();
    tile.setId(UUID.randomUUID());
    var prefWithTile = prefWithDefaultTile(tile);

    // One principal instance: principal() builds a new object each call, which strict stubbing
    // treats as a different argument.
    var caller = principal();
    when(preferenceRepository.findByUserId(USER_ID)).thenReturn(prefWithTile);
    when(preferenceRepository.patch(USER_ID, UserPreferencePatch.avatarOnly(null)))
        .thenReturn(prefWithTile);
    when(tileAccess.canUse(tile.getId(), caller)).thenReturn(false);

    var response = endpoint.deleteAvatar(caller);

    assertNull(response.getBody().defaultTile());
  }

  private static UserPreference prefWithDefaultTile(MapTile tile) {
    return new UserPreference(
        "preferred-user",
        "en",
        MeasurementSystem.METRIC,
        TemperatureUnit.CELSIUS,
        "UTC",
        ZoomBehavior.CENTER,
        null,
        null,
        null,
        tile);
  }

  private static UserPreference pref(UUID avatarMediaId) {
    return new UserPreference(
        "preferred-user",
        "en",
        MeasurementSystem.METRIC,
        TemperatureUnit.CELSIUS,
        "UTC",
        ZoomBehavior.CENTER,
        avatarMediaId,
        null,
        null,
        null);
  }

  private static MediaRecord mediaRecord(UUID id, String filename, String mimeType) {
    return new MediaRecord(
        id,
        "media/" + id + "/" + filename,
        filename,
        MediaContentType.IMAGE,
        1024,
        MediaAccess.LOGGED_IN,
        USER_ID,
        USER_ID,
        Instant.now(),
        mimeType,
        null,
        null,
        null);
  }

  private static MultipartFile mockMultipartFile(String filename, String contentType, byte[] data)
      throws IOException {
    var file = mock(MultipartFile.class);
    when(file.getOriginalFilename()).thenReturn(filename);
    when(file.getContentType()).thenReturn(contentType);
    when(file.getSize()).thenReturn((long) data.length);
    when(file.getInputStream()).thenReturn(new ByteArrayInputStream(data));
    return file;
  }
}
