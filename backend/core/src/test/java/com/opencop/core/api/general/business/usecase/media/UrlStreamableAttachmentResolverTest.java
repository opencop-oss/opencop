// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.model.media.MediaAccess;
import com.opencop.core.sdk.model.media.MediaUrlRecord;
import com.opencop.core.sdk.model.media.MediaUsage;
import com.opencop.core.sdk.model.media.StreamKind;
import com.opencop.core.sdk.ports.gateway.media.MediaUrlRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class UrlStreamableAttachmentResolverTest {

  private static final UUID ATTACHMENT_ID = UUID.randomUUID();

  private final MediaUrlRepository repository = mock(MediaUrlRepository.class);
  private final UrlStreamableAttachmentResolver resolver =
      new UrlStreamableAttachmentResolver(repository);

  @ParameterizedTest
  @CsvSource({
    "rtsp://camera.local/stream, RTSP_URL",
    "rtsps://camera.local:322/stream, RTSP_URL",
    "srt://relay.local:9000, SRT_URL",
    "srt://relay.local:9000?streamid=compass&latency=200, SRT_URL"
  })
  void resolvesConvertibleSchemesWithTheirSourceType(
      String url, StreamableAttachmentSourceType expected) {
    when(repository.findById(ATTACHMENT_ID)).thenReturn(Optional.of(record(url)));

    var attachment = resolver.resolve(ATTACHMENT_ID).orElseThrow();

    assertEquals(expected, attachment.sourceType());
    assertEquals(url, attachment.sourceUrl());
    assertEquals("att_" + ATTACHMENT_ID, attachment.pathName());
    assertEquals(ATTACHMENT_ID, attachment.attachmentId());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://cdn.example.com/live.m3u8",
        "http://10.0.4.7/mjpg/video.cgi",
        "https://cdn.example.com/feed.ts",
        "ftp://host/file",
        "SRT://relay.local:9000",
        "RTSP://camera.local/stream",
        "srt:relay.local"
      })
  void doesNotResolveOtherUrls(String url) {
    when(repository.findById(ATTACHMENT_ID)).thenReturn(Optional.of(record(url)));

    assertTrue(resolver.resolve(ATTACHMENT_ID).isEmpty());
  }

  @Test
  void doesNotResolveAnUnknownAttachment() {
    when(repository.findById(ATTACHMENT_ID)).thenReturn(Optional.empty());

    assertTrue(resolver.resolve(ATTACHMENT_ID).isEmpty());
  }

  private static MediaUrlRecord record(String url) {
    return new MediaUrlRecord(
        ATTACHMENT_ID,
        url,
        StreamKind.SRT,
        MediaAccess.SCOPED,
        UUID.randomUUID(),
        UUID.randomUUID(),
        Instant.now(),
        MediaUsage.EVENT_DETAIL);
  }
}
