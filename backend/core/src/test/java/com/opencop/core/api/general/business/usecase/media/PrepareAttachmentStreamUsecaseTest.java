// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.event.business.policy.EventAttachmentPolicy;
import com.opencop.core.api.general.ports.gateway.media.MediaStreamGateway;
import com.opencop.core.sdk.model.media.MediaAccess;
import com.opencop.core.sdk.model.media.MediaUrlRecord;
import com.opencop.core.sdk.model.media.MediaUsage;
import com.opencop.core.sdk.model.media.StreamKind;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.media.MediaUrlRepository;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class PrepareAttachmentStreamUsecaseTest {

  @Mock private EventAttachmentPolicy policy;
  @Mock private MediaUrlRepository mediaUrlRepository;
  @Mock private MediaStreamGateway gateway;
  private PrepareAttachmentStreamUsecase usecase;

  private static final UUID SOURCE_ID = UUID.randomUUID();
  private static final String OBJECT_ID = "event-abc";
  private static final UUID ATTACHMENT_ID = UUID.randomUUID();
  private static final String ATTACHMENT_PATH = "att_" + ATTACHMENT_ID;
  private static final String HLS_URL = "/streams/" + ATTACHMENT_PATH + "/index.m3u8";

  private OpenCopPrincipal principal;

  @BeforeEach
  void setUp() {
    usecase =
        new PrepareAttachmentStreamUsecase(
            policy, List.of(new UrlStreamableAttachmentResolver(mediaUrlRepository)), gateway);
    principal =
        com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(UUID.randomUUID());
  }

  @Test
  void prepare_whenGatewaySucceeds_returnsHlsUrl() {
    var rtspUrl = "rtsp://camera.example.com:554/stream";
    when(mediaUrlRepository.findById(ATTACHMENT_ID)).thenReturn(Optional.of(urlRecord(rtspUrl)));
    when(gateway.prepareStream(ATTACHMENT_PATH, rtspUrl)).thenReturn(Optional.of(HLS_URL));

    var result = usecase.prepare(principal, SOURCE_ID, OBJECT_ID, ATTACHMENT_ID);

    assertTrue(result.isPresent());
    assertEquals(HLS_URL, result.get());
    verify(policy).assertCanGet(principal, SOURCE_ID, OBJECT_ID, ATTACHMENT_ID);
  }

  @Test
  void prepare_forSrtUrl_registersThePathWithTheSrtSource() {
    var srtUrl = "srt://relay.example.com:9000?streamid=compass";
    when(mediaUrlRepository.findById(ATTACHMENT_ID))
        .thenReturn(Optional.of(urlRecord(srtUrl, StreamKind.SRT)));
    when(gateway.prepareStream(ATTACHMENT_PATH, srtUrl)).thenReturn(Optional.of(HLS_URL));

    var result = usecase.prepare(principal, SOURCE_ID, OBJECT_ID, ATTACHMENT_ID);

    assertEquals(Optional.of(HLS_URL), result);
    verify(policy).assertCanGet(principal, SOURCE_ID, OBJECT_ID, ATTACHMENT_ID);
  }

  @Test
  void prepare_whenGatewayUnavailable_returnsEmpty() {
    var rtspUrl = "rtsp://camera.example.com:554/stream";
    when(mediaUrlRepository.findById(ATTACHMENT_ID)).thenReturn(Optional.of(urlRecord(rtspUrl)));
    when(gateway.prepareStream(any(), any())).thenReturn(Optional.empty());

    assertTrue(usecase.prepare(principal, SOURCE_ID, OBJECT_ID, ATTACHMENT_ID).isEmpty());
  }

  @Test
  void prepare_whenNotUrlAttachment_throws422() {
    when(mediaUrlRepository.findById(ATTACHMENT_ID)).thenReturn(Optional.empty());

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> usecase.prepare(principal, SOURCE_ID, OBJECT_ID, ATTACHMENT_ID));

    assertEquals(422, ex.getHttpStatus().value());
    assertEquals("Attachment is not a convertible stream", ex.getMessage());
    verify(gateway, never()).prepareStream(any(), any());
  }

  @Test
  void prepare_whenUrlIsHttp_throws422() {
    when(mediaUrlRepository.findById(ATTACHMENT_ID))
        .thenReturn(Optional.of(urlRecord("http://example.com/video.mp4")));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> usecase.prepare(principal, SOURCE_ID, OBJECT_ID, ATTACHMENT_ID));

    assertEquals(422, ex.getHttpStatus().value());
    verify(gateway, never()).prepareStream(any(), any());
  }

  @Test
  void prepare_rtspsScheme_isAccepted() {
    var rtspUrl = "rtsps://secure-camera.example.com:322/stream";
    when(mediaUrlRepository.findById(ATTACHMENT_ID)).thenReturn(Optional.of(urlRecord(rtspUrl)));
    when(gateway.prepareStream(ATTACHMENT_PATH, rtspUrl)).thenReturn(Optional.of(HLS_URL));

    assertTrue(usecase.prepare(principal, SOURCE_ID, OBJECT_ID, ATTACHMENT_ID).isPresent());
  }

  @Test
  void prepare_derivesAttachmentPathName() {
    var rtspUrl = "rtsp://camera.example.com:554/stream";
    when(mediaUrlRepository.findById(ATTACHMENT_ID)).thenReturn(Optional.of(urlRecord(rtspUrl)));
    when(gateway.prepareStream(any(), any())).thenReturn(Optional.empty());
    var captor = ArgumentCaptor.forClass(String.class);

    usecase.prepare(principal, SOURCE_ID, OBJECT_ID, ATTACHMENT_ID);
    usecase.prepare(principal, SOURCE_ID, OBJECT_ID, ATTACHMENT_ID);

    verify(gateway, times(2)).prepareStream(captor.capture(), eq(rtspUrl));
    var first = captor.getAllValues().get(0);
    var second = captor.getAllValues().get(1);
    assertEquals(ATTACHMENT_PATH, first);
    assertEquals(first, second);
  }

  @Test
  void prepare_sameRtspUrl_differentAttachments_produceDifferentAttachmentPathNames() {
    var sharedUrl = "rtsp://shared-camera.example.com/stream";
    var attachmentB = UUID.randomUUID();
    when(mediaUrlRepository.findById(ATTACHMENT_ID)).thenReturn(Optional.of(urlRecord(sharedUrl)));
    when(mediaUrlRepository.findById(attachmentB)).thenReturn(Optional.of(urlRecord(sharedUrl)));
    when(gateway.prepareStream(any(), any())).thenReturn(Optional.empty());
    var captor = ArgumentCaptor.forClass(String.class);

    usecase.prepare(principal, SOURCE_ID, OBJECT_ID, ATTACHMENT_ID);
    usecase.prepare(principal, SOURCE_ID, OBJECT_ID, attachmentB);

    verify(gateway, times(2)).prepareStream(captor.capture(), any());
    assertEquals(ATTACHMENT_PATH, captor.getAllValues().get(0));
    assertEquals("att_" + attachmentB, captor.getAllValues().get(1));
  }

  @Test
  void prepare_whenPolicyThrows_gatewayIsNeverCalled() {
    var denied = new GlobalOpenCopException("Forbidden", HttpStatus.FORBIDDEN);
    org.mockito.Mockito.doThrow(denied)
        .when(policy)
        .assertCanGet(principal, SOURCE_ID, OBJECT_ID, ATTACHMENT_ID);

    assertThrows(
        GlobalOpenCopException.class,
        () -> usecase.prepare(principal, SOURCE_ID, OBJECT_ID, ATTACHMENT_ID));

    verify(gateway, never()).prepareStream(any(), any());
  }

  private MediaUrlRecord urlRecord(String url) {
    return urlRecord(url, StreamKind.RTSP);
  }

  private MediaUrlRecord urlRecord(String url, StreamKind kind) {
    return new MediaUrlRecord(
        ATTACHMENT_ID,
        url,
        kind,
        MediaAccess.SCOPED,
        UUID.randomUUID(),
        UUID.randomUUID(),
        Instant.now(),
        MediaUsage.EVENT_DETAIL);
  }
}
