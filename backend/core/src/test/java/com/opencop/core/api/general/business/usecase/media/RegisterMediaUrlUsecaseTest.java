// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.policy.media.StreamTransportPolicy;
import com.opencop.core.sdk.model.media.MediaAccess;
import com.opencop.core.sdk.model.media.MediaUrlRecord;
import com.opencop.core.sdk.model.media.MediaUsage;
import com.opencop.core.sdk.model.media.StreamKind;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.media.MediaUrlRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class RegisterMediaUrlUsecaseTest {

  private static final String CREDENTIALS_ERROR =
      "URL must not contain credentials because they could be persisted or forwarded";
  private MediaUrlRepository repository;
  private RegisterMediaUrlUsecase usecase;

  private static final UUID USER_ID = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    repository = mock(MediaUrlRepository.class);
    usecase = new RegisterMediaUrlUsecase(repository, new StreamTransportPolicy());
  }

  // --- happy paths, one per kind -------------------------------------------------

  @Test
  void rtspUrl_savesWithRtspKind() {
    assertRegisters("rtsp://camera.local/stream", StreamKind.RTSP, StreamKind.RTSP);
  }

  @Test
  void rtspsUrl_savesWithRtspKind() {
    assertRegisters("rtsps://camera.local/stream", StreamKind.RTSP, StreamKind.RTSP);
  }

  @Test
  void srtUrl_savesWithSrtKind() {
    assertRegisters("srt://relay.local:9000", StreamKind.SRT, StreamKind.SRT);
  }

  @Test
  void srtUrlWithStreamidAndLatency_savesWithSrtKind() {
    assertRegisters("srt://relay.local:9000?streamid=compass&latency=200", null, StreamKind.SRT);
  }

  @Test
  void srtUrlWithPassphrase_throws400() {
    var ex = assertRejects("srt://relay.local:9000?passphrase=supersecretpassphrase", null);
    assertEquals(
        "SRT URLs support only the streamid and latency query parameters", ex.getMessage());
  }

  @Test
  void srtUrlWithCredentials_throws400() {
    var ex = assertRejects("srt://user:pw@relay.local:9000", null);
    assertEquals(CREDENTIALS_ERROR, ex.getMessage());
  }

  @Test
  void srtUrlWithoutPort_throws400() {
    var ex = assertRejects("srt://relay.local?streamid=compass", null);
    assertEquals("SRT URLs need a port", ex.getMessage());
  }

  @Test
  void srtUrlWithAPortOutsideTheValidRange_throws400WithoutSaving() {
    for (var url : new String[] {"srt://relay.local:0", "srt://relay.local:65536"}) {
      var ex = assertRejects(url, null);
      assertEquals("SRT port must be between 1 and 65535", ex.getMessage());
    }
  }

  @Test
  void srtUrlWithTheHighestPort_isAccepted() {
    assertRegisters("srt://relay.local:65535", null, StreamKind.SRT);
  }

  @Test
  void srtUrlWithFragment_throws400() {
    var ex = assertRejects("srt://relay.local:9000?streamid=compass#frag", null);
    assertEquals("SRT URLs must not contain a fragment", ex.getMessage());
  }

  @Test
  void rtspUrlWithFragment_throws400() {
    var ex = assertRejects("rtsp://camera.local/stream#frag", StreamKind.RTSP);
    assertEquals("RTSP URLs must not contain a fragment", ex.getMessage());
  }

  @Test
  void httpUrlWithFragment_isStillAccepted() {
    assertRegisters("https://cdn.example.com/live.m3u8#t=0", null, StreamKind.HLS);
  }

  @Test
  void convertedUrlOverTheLengthCap_throws400() {
    var ex = assertRejects("rtsp://camera.local/" + "a".repeat(2100), StreamKind.RTSP);
    assertEquals("URL must not be longer than 2048 characters", ex.getMessage());
  }

  @Test
  void uppercaseScheme_isStoredLowercased() {
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var saved =
        usecase.register(
            USER_ID,
            "SRT://relay.local:9000?streamid=compass",
            null,
            MediaAccess.SCOPED,
            MediaUsage.EVENT_DETAIL);

    assertEquals("srt://relay.local:9000?streamid=compass", saved.url());
    assertEquals(StreamKind.SRT, saved.streamKind());
  }

  @Test
  void tsUrlWithoutKind_infersMpegts() {
    assertRegisters("https://cdn.example.com/feed.ts", null, StreamKind.MPEGTS);
  }

  @Test
  void explicitMpegtsOverHttp_isAccepted() {
    assertRegisters("http://10.0.4.7/stream", StreamKind.MPEGTS, StreamKind.MPEGTS);
  }

  @Test
  void mpegtsKindWithRtspUrl_throws400() {
    assertRejects("rtsp://camera.local/stream", StreamKind.MPEGTS);
  }

  @Test
  void hlsUrl_savesWithHlsKind() {
    assertRegisters("https://cdn.example.com/live.m3u8", StreamKind.HLS, StreamKind.HLS);
  }

  @Test
  void cameraUrl_savesWithMjpegKind() {
    assertRegisters("http://10.0.4.7/mjpg/video.cgi", StreamKind.MJPEG, StreamKind.MJPEG);
  }

  /** The case the old blanket query-param rejection made impossible. */
  @Test
  void cameraUrlWithQueryParams_isAccepted() {
    assertRegisters(
        "http://10.0.4.7/axis-cgi/mjpg/video.cgi?resolution=640x480",
        StreamKind.MJPEG,
        StreamKind.MJPEG);
  }

  @Test
  void hlsUrlWithQueryParams_isAccepted() {
    assertRegisters("https://cdn.example.com/live.m3u8?token=abc", StreamKind.HLS, StreamKind.HLS);
  }

  // --- inference when the caller omits the kind ----------------------------------

  @Test
  void rtspUrlWithoutKind_infersRtsp() {
    assertRegisters("rtsp://camera.local/stream", null, StreamKind.RTSP);
  }

  @Test
  void m3u8UrlWithoutKind_infersHls() {
    assertRegisters("https://cdn.example.com/live.m3u8", null, StreamKind.HLS);
  }

  @Test
  void ambiguousHttpUrlWithoutKind_throws400() {
    var ex = assertRejects("http://10.0.4.7/mjpg/video.cgi", null);
    assertEquals(
        "streamKind is required for http URLs that are not an .m3u8 or .ts stream",
        ex.getMessage());
  }

  // --- scheme must match the claimed kind ----------------------------------------

  @Test
  void rtspKindWithHttpUrl_throws400() {
    assertRejects("http://camera.local/stream", StreamKind.RTSP);
  }

  @Test
  void cameraKindWithRtspUrl_throws400() {
    assertRejects("rtsp://camera.local/stream", StreamKind.MJPEG);
  }

  @Test
  void hlsKindWithRtspUrl_throws400() {
    assertRejects("rtsp://camera.local/stream", StreamKind.HLS);
  }

  // --- rules that apply regardless of kind ---------------------------------------

  @Test
  void blankUrl_throws400() {
    assertRejects("  ", StreamKind.RTSP);
  }

  @Test
  void relativeUrl_throws400() {
    assertRejects("/relative/path", StreamKind.RTSP);
  }

  @Test
  void disallowedScheme_throws400() {
    assertRejects("javascript:alert(1)", null);
  }

  /** A scheme entirely outside the allowlist must not be blamed on a missing streamKind. */
  @Test
  void ftpScheme_throws400WithUnsupportedSchemeMessage() {
    var ex = assertRejects("ftp://host/file", null);
    assertEquals(
        "Unsupported URL scheme 'ftp'. Allowed: http, https, rtsp, rtsps, srt", ex.getMessage());
  }

  @Test
  void urlWithoutHost_throws400() {
    assertRejects("rtsp:camera", StreamKind.RTSP);
  }

  @Test
  void rtspUrlWithCredentials_throws400() {
    var ex = assertRejects("rtsp://admin:secret@camera.local/stream", StreamKind.RTSP);
    assertEquals(CREDENTIALS_ERROR, ex.getMessage());
  }

  @Test
  void rtspUrlWithUsernameOnly_throws400() {
    var ex = assertRejects("rtsp://admin@camera.local/stream", StreamKind.RTSP);
    assertEquals(CREDENTIALS_ERROR, ex.getMessage());
  }

  /** Cameras are anonymous on the LAN, so credentials stay rejected on the new kinds too. */
  @Test
  void cameraUrlWithCredentials_throws400() {
    var ex = assertRejects("http://admin:secret@10.0.4.7/mjpg/video.cgi", StreamKind.MJPEG);
    assertEquals(CREDENTIALS_ERROR, ex.getMessage());
  }

  @Test
  void rtspUrlWithQueryParams_throws400() {
    var ex = assertRejects("rtsp://camera.local/stream?token=abc", StreamKind.RTSP);
    assertEquals("RTSP URLs must not contain query parameters", ex.getMessage());
  }

  // --- helpers -------------------------------------------------------------------

  private void assertRegisters(String url, StreamKind requested, StreamKind expected) {
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var result =
        usecase.register(USER_ID, url, requested, MediaAccess.SCOPED, MediaUsage.EVENT_DETAIL);

    var captor = ArgumentCaptor.forClass(MediaUrlRecord.class);
    verify(repository).save(captor.capture());
    assertEquals(url, captor.getValue().url());
    assertEquals(expected, captor.getValue().streamKind());
    assertEquals(expected, result.streamKind());
  }

  private GlobalOpenCopException assertRejects(String url, StreamKind requested) {
    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                usecase.register(
                    USER_ID, url, requested, MediaAccess.SCOPED, MediaUsage.EVENT_DETAIL));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    verify(repository, never()).save(any());
    return ex;
  }
}
