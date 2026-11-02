// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.usecase.attachment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.event.business.policy.EventAttachmentPolicy;
import com.opencop.core.api.event.ports.business.usecase.attachment.DeleteEventAttachmentCommand;
import com.opencop.core.api.general.ports.usecase.media.DeleteMedia;
import com.opencop.core.api.general.ports.usecase.media.MediaNotFoundException;
import com.opencop.core.api.general.ports.usecase.media.MediaView;
import com.opencop.core.api.general.ports.usecase.media.RetrieveMedia;
import com.opencop.core.api.general.ports.usecase.media.ThumbnailRef;
import com.opencop.core.sdk.model.media.MediaAccess;
import com.opencop.core.sdk.model.media.MediaContentType;
import com.opencop.core.sdk.model.media.MediaRecord;
import com.opencop.core.sdk.model.media.MediaUsage;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.media.EventAttachmentRepository;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
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
class DeleteFileEventAttachmentUsecaseTest {

  @Mock private RetrieveMedia retrieveMedia;
  @Mock private DeleteMedia deleteMedia;
  @Mock private EventAttachmentRepository eventAttachmentRepository;
  @Mock private EventAttachmentPolicy attachmentPolicy;

  private DeleteFileEventAttachmentUsecase usecase;

  private static final UUID SOURCE_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();
  private static final String OBJECT_ID = UUID.randomUUID().toString();

  @BeforeEach
  void setUp() {
    usecase =
        new DeleteFileEventAttachmentUsecase(
            retrieveMedia, deleteMedia, eventAttachmentRepository, attachmentPolicy);
  }

  @Test
  void resolve_whenFileAttachmentDeletesThumbnailsMediaAndAssociation()
      throws MediaNotFoundException {
    var mediaId = UUID.randomUUID();
    var thumbId = UUID.randomUUID();
    var command = command(mediaId);
    when(retrieveMedia.getWithoutAccessCheck(mediaId))
        .thenReturn(
            new MediaView(
                mediaRecord(mediaId, USER_ID),
                "url",
                List.of(new ThumbnailRef(thumbId, "thumb-url", 128, 128, "image/webp"))));

    var resolved = usecase.resolve(command).orElseThrow();

    resolved.delete();
    verify(deleteMedia).delete(thumbId);
    verify(deleteMedia).delete(mediaId);
    verify(eventAttachmentRepository).delete(SOURCE_ID, OBJECT_ID, mediaId);
  }

  @Test
  void resolve_whenMediaNotFoundReturnsEmpty() throws MediaNotFoundException {
    var mediaId = UUID.randomUUID();
    when(retrieveMedia.getWithoutAccessCheck(mediaId))
        .thenThrow(new MediaNotFoundException(mediaId));

    var resolved = usecase.resolve(command(mediaId));

    assertFalse(resolved.isPresent());
    verify(deleteMedia, never()).delete(any());
  }

  @Test
  void resolve_whenLegacyGlobalNotFoundReturnsEmpty() throws MediaNotFoundException {
    var mediaId = UUID.randomUUID();
    when(retrieveMedia.getWithoutAccessCheck(mediaId))
        .thenThrow(new GlobalOpenCopException("not found", HttpStatus.NOT_FOUND));

    var resolved = usecase.resolve(command(mediaId));

    assertFalse(resolved.isPresent());
    verify(deleteMedia, never()).delete(any());
  }

  @Test
  void resolve_whenPolicyRejectsOwnership_throwsAndDoesNotDelete() throws MediaNotFoundException {
    var mediaId = UUID.randomUUID();
    var otherUser = UUID.randomUUID();
    when(retrieveMedia.getWithoutAccessCheck(mediaId))
        .thenReturn(new MediaView(mediaRecord(mediaId, otherUser), "url", List.of()));
    doThrow(new GlobalOpenCopException("forbidden", HttpStatus.FORBIDDEN))
        .when(attachmentPolicy)
        .assertOwnerOrAdmin(any(OpenCopPrincipal.class), any(UUID.class));

    var resolved = usecase.resolve(command(mediaId)).orElseThrow();

    var ex = assertThrows(GlobalOpenCopException.class, resolved::delete);

    assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    verify(deleteMedia, never()).delete(any());
  }

  @Test
  void resolve_whenPolicyAllows_deletesMediaAndAssociation() throws MediaNotFoundException {
    var mediaId = UUID.randomUUID();
    var ownerId = UUID.randomUUID();
    when(retrieveMedia.getWithoutAccessCheck(mediaId))
        .thenReturn(new MediaView(mediaRecord(mediaId, ownerId), "url", List.of()));

    var resolved = usecase.resolve(command(mediaId)).orElseThrow();

    resolved.delete();
    verify(attachmentPolicy).assertOwnerOrAdmin(any(OpenCopPrincipal.class), eq(ownerId));
    verify(deleteMedia).delete(mediaId);
    verify(eventAttachmentRepository).delete(SOURCE_ID, OBJECT_ID, mediaId);
  }

  private static DeleteEventAttachmentCommand command(UUID attachmentId) {
    return new DeleteEventAttachmentCommand(principal(), SOURCE_ID, OBJECT_ID, attachmentId);
  }

  private static MediaRecord mediaRecord(UUID id, UUID ownerId) {
    return new MediaRecord(
        id,
        "media/" + id + "/photo.jpg",
        "photo.jpg",
        MediaContentType.IMAGE,
        1024,
        MediaAccess.SCOPED,
        ownerId,
        ownerId,
        Instant.now(),
        "image/jpeg",
        null,
        null,
        MediaUsage.EVENT_DETAIL);
  }

  private static OpenCopPrincipal principal() {
    return com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(USER_ID);
  }
}
