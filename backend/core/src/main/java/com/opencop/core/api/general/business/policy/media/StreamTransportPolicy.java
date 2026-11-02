// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.media;

import com.opencop.core.sdk.model.media.StreamKind;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Business rules for a URL media attachment's transport: which schemes each {@link StreamKind}
 * accepts, whether its URLs may carry a query string, and how to resolve a kind when the caller
 * doesn't supply one.
 */
@Component
public class StreamTransportPolicy {

  private static final Set<String> RTSP_SCHEMES = Set.of("rtsp", "rtsps");
  private static final Set<String> HTTP_SCHEMES = Set.of("http", "https");
  private static final Set<String> SRT_SCHEMES = Set.of("srt");

  private static final String SRT_STREAMID = "streamid";
  private static final String SRT_LATENCY = "latency";

  /**
   * The only SRT query parameters accepted: {@code passphrase}/{@code pbkeylen} are credentials,
   * and {@code mode=listener} would make the converter open a port instead of pulling. The shell
   * adapter in opencop-infra enforces the same rules (it is the backstop for URLs that never pass
   * through here), so a change to this grammar must be made in both.
   */
  private static final Set<String> SRT_QUERY_KEYS = Set.of(SRT_STREAMID, SRT_LATENCY);

  /**
   * {@code streamid} is deliberately a plain token. It is stored, passed through MediaMTX and
   * ffmpeg's argv, and visible in their diagnostics, so it must not be treated as a secret; the
   * restricted charset also keeps percent-encoding and control characters out.
   */
  private static final Pattern SRT_STREAMID_VALUE = Pattern.compile("[A-Za-z0-9._~-]{1,128}");

  /** Milliseconds; the adapter converts to the microseconds ffmpeg's libsrt expects. */
  private static final Pattern SRT_LATENCY_VALUE = Pattern.compile("[0-9]{1,6}");

  /** The converter receives the URL as a single argv entry, so very long URLs can never play. */
  private static final int MAX_CONVERTED_URL_LENGTH = 2048;

  private static final int MIN_PORT = 1;
  private static final int MAX_PORT = 65535;

  /**
   * Resolves the kind to store: the caller's claim if given, otherwise inferred from the scheme and
   * path. Validates the result against the scheme either way.
   */
  public StreamKind resolveKind(StreamKind requestedKind, String scheme, String path) {
    var kind = requestedKind != null ? requestedKind : infer(scheme, path);
    requireSchemeMatchesKind(kind, scheme);
    return kind;
  }

  /** Pass the raw (still percent-encoded) query so an encoded {@code &} can't split a value. */
  public void assertQueryAllowed(StreamKind kind, String query) {
    if (query == null) return;
    switch (kind) {
      case RTSP ->
          throw new GlobalOpenCopException(
              kind + " URLs must not contain query parameters", HttpStatus.BAD_REQUEST);
      case SRT -> assertSrtQuery(query);
      case HLS, MJPEG, MPEGTS -> {}
    }
  }

  /**
   * Checks only the kinds that are pulled by the converter, which is handed the URL as one command
   * argument: no fragment (meaningless to it, and the adapter refuses it), a bounded length, and
   * for SRT an explicit port in 1..65535 (ffmpeg has no default).
   */
  public static void assertConversionInputAllowed(StreamKind kind, URI uri, int urlLength) {
    switch (kind) {
      case RTSP, SRT -> {
        if (urlLength > MAX_CONVERTED_URL_LENGTH) {
          throw new GlobalOpenCopException(
              "URL must not be longer than " + MAX_CONVERTED_URL_LENGTH + " characters",
              HttpStatus.BAD_REQUEST);
        }
        if (uri.getRawFragment() != null) {
          throw new GlobalOpenCopException(
              kind + " URLs must not contain a fragment", HttpStatus.BAD_REQUEST);
        }
        if (kind == StreamKind.SRT) {
          assertSrtPort(uri.getPort());
        }
      }
      case HLS, MJPEG, MPEGTS -> {}
    }
  }

  /** ffmpeg has no default port, and 0 or anything above 65535 can't name a destination. */
  private static void assertSrtPort(int port) {
    if (port == -1) {
      throw new GlobalOpenCopException("SRT URLs need a port", HttpStatus.BAD_REQUEST);
    }
    if (port < MIN_PORT || port > MAX_PORT) {
      throw new GlobalOpenCopException(
          "SRT port must be between " + MIN_PORT + " and " + MAX_PORT, HttpStatus.BAD_REQUEST);
    }
  }

  private static void assertSrtQuery(String query) {
    var seen = new HashSet<String>();
    for (var pair : query.split("&", -1)) {
      var separator = pair.indexOf('=');
      if (separator <= 0 || separator == pair.length() - 1) throw unsupportedSrtQuery();
      var key = pair.substring(0, separator);
      var value = pair.substring(separator + 1);
      if (!SRT_QUERY_KEYS.contains(key)) throw unsupportedSrtQuery();
      if (!seen.add(key)) {
        throw new GlobalOpenCopException(
            "SRT URLs must not repeat a query parameter", HttpStatus.BAD_REQUEST);
      }
      if (key.equals(SRT_STREAMID) && !SRT_STREAMID_VALUE.matcher(value).matches()) {
        throw new GlobalOpenCopException(
            "SRT streamid may only contain letters, digits and . _ ~ - (max 128 characters)",
            HttpStatus.BAD_REQUEST);
      }
      if (key.equals(SRT_LATENCY) && !SRT_LATENCY_VALUE.matcher(value).matches()) {
        throw new GlobalOpenCopException(
            "SRT latency must be a whole number of milliseconds (max 6 digits)",
            HttpStatus.BAD_REQUEST);
      }
    }
  }

  private static GlobalOpenCopException unsupportedSrtQuery() {
    return new GlobalOpenCopException(
        "SRT URLs support only the streamid and latency query parameters", HttpStatus.BAD_REQUEST);
  }

  // Exhaustive switch: a new StreamKind fails to compile here instead of matching the wrong set.
  private static Set<String> schemesFor(StreamKind kind) {
    return switch (kind) {
      case RTSP -> RTSP_SCHEMES;
      case HLS, MJPEG, MPEGTS -> HTTP_SCHEMES;
      case SRT -> SRT_SCHEMES;
    };
  }

  private static StreamKind infer(String scheme, String path) {
    var candidates =
        Arrays.stream(StreamKind.values()).filter(k -> schemesFor(k).contains(scheme)).toList();

    if (candidates.isEmpty()) {
      throw new GlobalOpenCopException(
          "Unsupported URL scheme '" + scheme + "'. Allowed: " + allowedSchemesDisplay(),
          HttpStatus.BAD_REQUEST);
    }
    if (candidates.size() == 1) {
      return candidates.get(0);
    }

    // Ambiguous among candidates (http(s) serves HLS, MPEG-TS and image feeds) — the path
    // extension disambiguates when present.
    var lowerPath = path == null ? "" : path.toLowerCase(Locale.ROOT);
    if (lowerPath.endsWith(".m3u8")) return StreamKind.HLS;
    if (lowerPath.endsWith(".ts")) return StreamKind.MPEGTS;
    throw new GlobalOpenCopException(
        "streamKind is required for " + scheme + " URLs that are not an .m3u8 or .ts stream",
        HttpStatus.BAD_REQUEST);
  }

  private static void requireSchemeMatchesKind(StreamKind kind, String scheme) {
    var allowed = schemesFor(kind);
    if (!allowed.contains(scheme)) {
      throw new GlobalOpenCopException(
          "streamKind "
              + kind
              + " requires a "
              + String.join(" or ", allowed.stream().map(s -> s + "://").sorted().toList())
              + " URL, but got '"
              + scheme
              + "'",
          HttpStatus.BAD_REQUEST);
    }
  }

  private static String allowedSchemesDisplay() {
    return Arrays.stream(StreamKind.values())
        .flatMap(k -> schemesFor(k).stream())
        .distinct()
        .sorted()
        .collect(Collectors.joining(", "));
  }
}
