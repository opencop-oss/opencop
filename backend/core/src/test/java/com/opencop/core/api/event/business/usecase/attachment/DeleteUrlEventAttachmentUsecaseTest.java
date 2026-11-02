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
import com.opencop.core.sdk.model.media.MediaAccess;
import com.opencop.core.sdk.model.media.MediaUrlRecord;
import com.opencop.core.sdk.model.media.MediaUsage;
import com.opencop.core.sdk.model.media.StreamKind;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.media.EventAttachmentRepository;
import com.opencop.core.sdk.ports.gateway.media.MediaUrlRepository;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class DeleteUrlEventAttachmentUsecaseTest {

  @Mock private MediaUrlRepository mediaUrlRepository;
  @Mock private EventAttachmentRepository eventAttachmentRepository;
  @Mock private EventAttachmentPolicy attachmentPolicy;

  private DeleteUrlEventAttachmentUsecase usecase;

  private static final UUID SOURCE_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();
  private static final String OBJECT_ID = UUID.randomUUID().toString();

  @BeforeEach
  void setUp() {
    usecase =
        new DeleteUrlEventAttachmentUsecase(
            mediaUrlRepository, eventAttachmentRepository, attachmentPolicy);
  }

  @Test
  void resolve_whenUrlAttachmentDeletesUrlAndAssociation() {
    var urlId = UUID.randomUUID();
    when(mediaUrlRepository.findById(urlId))
        .thenReturn(Optional.of(urlRecord(urlId, USER_ID, "rtsp://camera.local/stream")));

    var resolved = usecase.resolve(command(urlId)).orElseThrow();

    resolved.delete();
    verify(mediaUrlRepository).deleteById(urlId);
    verify(eventAttachmentRepository).delete(SOURCE_ID, OBJECT_ID, urlId);
  }

  @Test
  void resolve_whenUrlNotFoundReturnsEmpty() {
    var urlId = UUID.randomUUID();

    var resolved = usecase.resolve(command(urlId));

    assertFalse(resolved.isPresent());
    verify(mediaUrlRepository, never()).deleteById(any());
  }

  @Test
  void resolve_whenPolicyRejectsOwnership_throwsAndDoesNotDelete() {
    var urlId = UUID.randomUUID();
    when(mediaUrlRepository.findById(urlId))
        .thenReturn(Optional.of(urlRecord(urlId, UUID.randomUUID(), "rtsp://camera.local/stream")));
    doThrow(new GlobalOpenCopException("forbidden", HttpStatus.FORBIDDEN))
        .when(attachmentPolicy)
        .assertOwnerOrAdmin(any(OpenCopPrincipal.class), any(UUID.class));

    var resolved = usecase.resolve(command(urlId)).orElseThrow();

    var ex = assertThrows(GlobalOpenCopException.class, resolved::delete);

    assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    verify(mediaUrlRepository, never()).deleteById(any());
  }

  @Test
  void resolve_whenPolicyAllows_deletesUrlAndAssociation() {
    var urlId = UUID.randomUUID();
    var ownerId = UUID.randomUUID();
    when(mediaUrlRepository.findById(urlId))
        .thenReturn(Optional.of(urlRecord(urlId, ownerId, "rtsp://camera.local/stream")));

    var resolved = usecase.resolve(command(urlId)).orElseThrow();

    resolved.delete();
    verify(attachmentPolicy).assertOwnerOrAdmin(any(OpenCopPrincipal.class), eq(ownerId));
    verify(mediaUrlRepository).deleteById(urlId);
    verify(eventAttachmentRepository).delete(SOURCE_ID, OBJECT_ID, urlId);
  }

  private static DeleteEventAttachmentCommand command(UUID attachmentId) {
    return new DeleteEventAttachmentCommand(principal(), SOURCE_ID, OBJECT_ID, attachmentId);
  }

  private static MediaUrlRecord urlRecord(UUID id, UUID ownerId, String url) {
    return new MediaUrlRecord(
        id,
        url,
        StreamKind.RTSP,
        MediaAccess.SCOPED,
        ownerId,
        ownerId,
        Instant.now(),
        MediaUsage.EVENT_DETAIL);
  }

  private static OpenCopPrincipal principal() {
    return com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(USER_ID);
  }
}
