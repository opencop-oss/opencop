// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.usecase.attachment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.when;

import com.opencop.core.api.event.ports.business.usecase.attachment.GetEventAttachmentCommand;
import com.opencop.core.api.general.ports.usecase.media.AttachmentView;
import com.opencop.core.api.general.ports.usecase.media.MediaNotFoundException;
import com.opencop.core.api.general.ports.usecase.media.MediaView;
import com.opencop.core.api.general.ports.usecase.media.RetrieveMedia;
import com.opencop.core.sdk.model.media.MediaAccess;
import com.opencop.core.sdk.model.media.MediaContentType;
import com.opencop.core.sdk.model.media.MediaRecord;
import com.opencop.core.sdk.model.media.MediaUsage;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetFileEventAttachmentUsecaseTest {

  @Mock private RetrieveMedia retrieveMedia;

  private GetFileEventAttachmentUsecase usecase;

  private static final UUID SOURCE_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();
  private static final String OBJECT_ID = UUID.randomUUID().toString();

  @BeforeEach
  void setUp() {
    usecase = new GetFileEventAttachmentUsecase(retrieveMedia);
  }

  @Test
  void resolve_whenFileAttachmentReturnsFileView() throws MediaNotFoundException {
    var mediaId = UUID.randomUUID();
    var record = mediaRecord(mediaId);
    when(retrieveMedia.getWithoutAccessCheck(mediaId))
        .thenReturn(new MediaView(record, "url", List.of()));

    var result = usecase.resolve(command(mediaId)).orElseThrow();

    var fileView = assertInstanceOf(AttachmentView.File.class, result);
    assertEquals(record, fileView.record());
    assertEquals("url", fileView.url());
  }

  @Test
  void resolve_whenMediaNotFoundReturnsEmpty() throws MediaNotFoundException {
    var mediaId = UUID.randomUUID();
    when(retrieveMedia.getWithoutAccessCheck(mediaId))
        .thenThrow(new MediaNotFoundException(mediaId));

    var result = usecase.resolve(command(mediaId));

    assertFalse(result.isPresent());
  }

  private static GetEventAttachmentCommand command(UUID attachmentId) {
    return new GetEventAttachmentCommand(principal(), SOURCE_ID, OBJECT_ID, attachmentId);
  }

  private static MediaRecord mediaRecord(UUID id) {
    return new MediaRecord(
        id,
        "media/" + id + "/photo.jpg",
        "photo.jpg",
        MediaContentType.IMAGE,
        1024,
        MediaAccess.SCOPED,
        USER_ID,
        USER_ID,
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
