// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.opencop.core.sdk.model.media.StreamKind;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class StreamTransportPolicyTest {

  private final StreamTransportPolicy policy = new StreamTransportPolicy();

  // --- resolveKind: explicit kind ------------------------------------------------

  @Test
  void resolveKind_returnsExplicitKindWhenSchemeMatches() {
    assertEquals(StreamKind.RTSP, policy.resolveKind(StreamKind.RTSP, "rtsp", "/stream"));
    assertEquals(StreamKind.HLS, policy.resolveKind(StreamKind.HLS, "https", "/live.m3u8"));
    assertEquals(StreamKind.MJPEG, policy.resolveKind(StreamKind.MJPEG, "http", "/mjpg/video.cgi"));
  }

  @Test
  void resolveKind_rejectsExplicitKindWithMismatchedScheme() {
    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> policy.resolveKind(StreamKind.RTSP, "http", "/stream"));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    assertEquals(
        "streamKind RTSP requires a rtsp:// or rtsps:// URL, but got 'http'", ex.getMessage());
  }

  // --- resolveKind: inference (requestedKind == null) -----------------------------

  @Test
  void resolveKind_infersRtspFromScheme() {
    assertEquals(StreamKind.RTSP, policy.resolveKind(null, "rtsp", "/stream"));
    assertEquals(StreamKind.RTSP, policy.resolveKind(null, "rtsps", "/stream"));
  }

  @Test
  void resolveKind_infersHlsFromM3u8Path() {
    assertEquals(StreamKind.HLS, policy.resolveKind(null, "https", "/live.m3u8"));
  }

  @Test
  void resolveKind_infersMpegTsFromTsPath() {
    assertEquals(StreamKind.MPEGTS, policy.resolveKind(null, "https", "/live/feed.TS"));
  }

  @Test
  void resolveKind_rtspUrlEndingInTsStaysRtsp() {
    assertEquals(StreamKind.RTSP, policy.resolveKind(null, "rtsp", "/feed.ts"));
  }

  @Test
  void resolveKind_acceptsExplicitMpegTsOverPlainHttp() {
    assertEquals(StreamKind.MPEGTS, policy.resolveKind(StreamKind.MPEGTS, "http", "/stream/abc"));
  }

  @Test
  void resolveKind_acceptsExplicitMpegTsOverHttp() {
    assertEquals(StreamKind.MPEGTS, policy.resolveKind(StreamKind.MPEGTS, "https", "/stream/abc"));
  }

  @Test
  void resolveKind_rejectsMpegTsOverRtspScheme() {
    assertThrows(
        GlobalOpenCopException.class, () -> policy.resolveKind(StreamKind.MPEGTS, "rtsp", "/s"));
  }

  @Test
  void resolveKind_infersSrtFromScheme() {
    assertEquals(StreamKind.SRT, policy.resolveKind(null, "srt", "/"));
  }

  @Test
  void resolveKind_rejectsSrtKindOverHttp() {
    assertThrows(
        GlobalOpenCopException.class, () -> policy.resolveKind(StreamKind.SRT, "https", "/s"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "streamid=compass",
        "streamid=cam-1_a.b~c",
        "latency=200",
        "latency=0",
        "latency=999999",
        "streamid=compass&latency=200",
        "latency=200&streamid=compass"
      })
  void assertQueryAllowed_permitsSrtStreamidAndLatency(String query) {
    policy.assertQueryAllowed(StreamKind.SRT, query);
  }

  @Test
  void assertQueryAllowed_permitsNoSrtQuery() {
    policy.assertQueryAllowed(StreamKind.SRT, null);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "passphrase=supersecretpassphrase",
        "streamid=a&pbkeylen=32",
        "mode=listener",
        "StreamID=a",
        "Latency=5",
        "stream%69d=a",
        "",
        "streamid",
        "streamid=",
        "=value",
        "streamid=a&",
        "&streamid=a",
        "streamid=a&&latency=1"
      })
  void assertQueryAllowed_rejectsUnsupportedSrtQueries(String query) {
    var ex =
        assertThrows(
            GlobalOpenCopException.class, () -> policy.assertQueryAllowed(StreamKind.SRT, query));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    assertEquals(
        "SRT URLs support only the streamid and latency query parameters", ex.getMessage());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "streamid=a%26b",
        "streamid=a%0Ab",
        "streamid=a;b",
        "streamid=#!::r=x",
        "streamid=caméra",
        "streamid=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
      })
  void assertQueryAllowed_rejectsStreamidOutsideTheTokenCharset(String query) {
    var ex =
        assertThrows(
            GlobalOpenCopException.class, () -> policy.assertQueryAllowed(StreamKind.SRT, query));
    assertEquals(
        "SRT streamid may only contain letters, digits and . _ ~ - (max 128 characters)",
        ex.getMessage());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"latency=fast", "latency=-1", "latency=1.5", "latency=٣٠٠", "latency=1234567"})
  void assertQueryAllowed_rejectsInvalidSrtLatency(String query) {
    var ex =
        assertThrows(
            GlobalOpenCopException.class, () -> policy.assertQueryAllowed(StreamKind.SRT, query));
    assertEquals(
        "SRT latency must be a whole number of milliseconds (max 6 digits)", ex.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"streamid=a&streamid=b", "latency=1&latency=2"})
  void assertQueryAllowed_rejectsRepeatedSrtParameters(String query) {
    var ex =
        assertThrows(
            GlobalOpenCopException.class, () -> policy.assertQueryAllowed(StreamKind.SRT, query));
    assertEquals("SRT URLs must not repeat a query parameter", ex.getMessage());
  }

  // --- assertConversionInputAllowed -----------------------------------------------

  @Test
  void assertConversionInputAllowed_rejectsFragmentsAndMissingSrtPortAndLongUrls() {
    var withFragment = URI.create("srt://relay:9000?streamid=a#frag");
    assertEquals(
        "SRT URLs must not contain a fragment",
        assertThrows(
                GlobalOpenCopException.class,
                () ->
                    StreamTransportPolicy.assertConversionInputAllowed(
                        StreamKind.SRT, withFragment, 40))
            .getMessage());
    assertEquals(
        "SRT URLs need a port",
        assertThrows(
                GlobalOpenCopException.class,
                () ->
                    StreamTransportPolicy.assertConversionInputAllowed(
                        StreamKind.SRT, URI.create("srt://relay"), 10))
            .getMessage());
    assertEquals(
        "URL must not be longer than 2048 characters",
        assertThrows(
                GlobalOpenCopException.class,
                () ->
                    StreamTransportPolicy.assertConversionInputAllowed(
                        StreamKind.RTSP, URI.create("rtsp://cam/s"), 2049))
            .getMessage());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"srt://relay:0", "srt://relay:65536", "srt://relay:70000", "srt://relay:99999"})
  void assertConversionInputAllowed_rejectsSrtPortsOutsideTheValidRange(String url) {
    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                StreamTransportPolicy.assertConversionInputAllowed(
                    StreamKind.SRT, URI.create(url), 20));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    assertEquals("SRT port must be between 1 and 65535", ex.getMessage());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "srt://relay:1",
        "srt://relay:9000",
        "srt://relay:65535",
        "srt://relay:09000",
        "srt://[::1]:9000"
      })
  void assertConversionInputAllowed_acceptsSrtPortsInTheValidRange(String url) {
    StreamTransportPolicy.assertConversionInputAllowed(StreamKind.SRT, URI.create(url), 20);
  }

  @ParameterizedTest
  @EnumSource(
      value = StreamKind.class,
      names = {"HLS", "MJPEG", "MPEGTS"})
  void assertConversionInputAllowed_leavesUnconvertedKindsAlone(StreamKind kind) {
    StreamTransportPolicy.assertConversionInputAllowed(
        kind, URI.create("https://h/x.m3u8#t=0"), 100_000);
  }

  // --- every kind ---------------------------------------------------------------------

  @ParameterizedTest
  @EnumSource(StreamKind.class)
  void assertQueryAllowed_withoutAQueryIsAlwaysAllowed(StreamKind kind) {
    policy.assertQueryAllowed(kind, null);
  }

  @ParameterizedTest
  @EnumSource(
      value = StreamKind.class,
      names = {"HLS", "MJPEG", "MPEGTS"})
  void assertQueryAllowed_permitsAnyQueryForUnconvertedKinds(StreamKind kind) {
    policy.assertQueryAllowed(kind, "token=1&x=y");
  }

  @ParameterizedTest
  @EnumSource(StreamKind.class)
  void resolveKind_acceptsEveryKindOnItsOwnScheme(StreamKind kind) {
    var scheme =
        switch (kind) {
          case RTSP -> "rtsp";
          case SRT -> "srt";
          case HLS, MJPEG, MPEGTS -> "https";
        };

    assertEquals(kind, policy.resolveKind(kind, scheme, "/x"));
  }

  @Test
  void resolveKind_infersMpegTsFromLowercaseTsPathAndNotFromAMiddleSegment() {
    assertEquals(StreamKind.MPEGTS, policy.resolveKind(null, "https", "/live/feed.ts"));
    assertThrows(
        GlobalOpenCopException.class, () -> policy.resolveKind(null, "https", "/stream.ts/foo"));
    assertThrows(GlobalOpenCopException.class, () -> policy.resolveKind(null, "https", null));
  }

  @Test
  void assertQueryAllowed_permitsQueryForMpegTs() {
    policy.assertQueryAllowed(StreamKind.MPEGTS, "token=1");
  }

  @Test
  void resolveKind_ambiguousHttpWithoutM3u8Throws400() {
    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> policy.resolveKind(null, "http", "/mjpg/video.cgi"));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    assertEquals(
        "streamKind is required for http URLs that are not an .m3u8 or .ts stream",
        ex.getMessage());
  }

  @Test
  void resolveKind_unsupportedSchemeThrows400WithAllowedList() {
    var ex =
        assertThrows(GlobalOpenCopException.class, () -> policy.resolveKind(null, "ftp", "/f"));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    assertEquals(
        "Unsupported URL scheme 'ftp'. Allowed: http, https, rtsp, rtsps, srt", ex.getMessage());
  }

  // --- assertQueryAllowed ---------------------------------------------------------

  @Test
  void assertQueryAllowed_rejectsQueryForRtsp() {
    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> policy.assertQueryAllowed(StreamKind.RTSP, "token=abc"));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    assertEquals("RTSP URLs must not contain query parameters", ex.getMessage());
  }

  @Test
  void assertQueryAllowed_allowsQueryForHlsAndMjpeg() {
    policy.assertQueryAllowed(StreamKind.HLS, "token=abc");
    policy.assertQueryAllowed(StreamKind.MJPEG, "resolution=640x480");
  }

  @Test
  void assertQueryAllowed_allowsNoQueryForAnyKind() {
    policy.assertQueryAllowed(StreamKind.RTSP, null);
    policy.assertQueryAllowed(StreamKind.HLS, null);
    policy.assertQueryAllowed(StreamKind.MJPEG, null);
  }
}
