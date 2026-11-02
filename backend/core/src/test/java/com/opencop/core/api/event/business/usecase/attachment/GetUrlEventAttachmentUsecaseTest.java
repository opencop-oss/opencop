// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.usecase.attachment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.when;

import com.opencop.core.api.event.ports.business.usecase.attachment.GetEventAttachmentCommand;
import com.opencop.core.api.general.ports.usecase.media.AttachmentView;
import com.opencop.core.sdk.model.media.MediaAccess;
import com.opencop.core.sdk.model.media.MediaUrlRecord;
import com.opencop.core.sdk.model.media.MediaUsage;
import com.opencop.core.sdk.model.media.StreamKind;
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

@ExtendWith(MockitoExtension.class)
class GetUrlEventAttachmentUsecaseTest {

  @Mock private MediaUrlRepository mediaUrlRepository;

  private GetUrlEventAttachmentUsecase usecase;

  private static final UUID SOURCE_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();
  private static final String OBJECT_ID = UUID.randomUUID().toString();

  @BeforeEach
  void setUp() {
    usecase = new GetUrlEventAttachmentUsecase(mediaUrlRepository);
  }

  @Test
  void resolve_whenUrlAttachmentReturnsUrlView() {
    var urlId = UUID.randomUUID();
    var record = urlRecord(urlId, "rtsp://camera.local/stream");
    when(mediaUrlRepository.findById(urlId)).thenReturn(Optional.of(record));

    var result = usecase.resolve(command(urlId)).orElseThrow();

    var urlView = assertInstanceOf(AttachmentView.Url.class, result);
    assertEquals(record, urlView.record());
  }

  @Test
  void resolve_whenUrlNotFoundReturnsEmpty() {
    var urlId = UUID.randomUUID();

    var result = usecase.resolve(command(urlId));

    assertFalse(result.isPresent());
  }

  private static GetEventAttachmentCommand command(UUID attachmentId) {
    return new GetEventAttachmentCommand(principal(), SOURCE_ID, OBJECT_ID, attachmentId);
  }

  private static MediaUrlRecord urlRecord(UUID id, String url) {
    return new MediaUrlRecord(
        id,
        url,
        StreamKind.RTSP,
        MediaAccess.SCOPED,
        USER_ID,
        USER_ID,
        Instant.now(),
        MediaUsage.EVENT_DETAIL);
  }

  private static OpenCopPrincipal principal() {
    return com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(USER_ID);
  }
}
