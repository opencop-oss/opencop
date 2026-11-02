// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.media;

import com.opencop.core.api.general.business.policy.media.StreamTransportPolicy;
import com.opencop.core.sdk.model.media.MediaAccess;
import com.opencop.core.sdk.model.media.MediaUrlRecord;
import com.opencop.core.sdk.model.media.MediaUsage;
import com.opencop.core.sdk.model.media.StreamKind;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.media.MediaUrlRepository;
import com.opencop.core.sdk.ports.usecase.media.RegisterMediaUrl;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * Registers a URL media attachment after validating it against the transport the caller claims.
 *
 * <p>Validation stops at the URL string: whether an http(s) endpoint actually serves an image feed,
 * an HLS manifest, or a web page is not knowable without fetching it, and fetching an
 * operator-supplied URL server-side is both an SSRF primitive and unreliable for cameras that are
 * only reachable from the operator's network. Wrong content surfaces as a playback failure instead.
 *
 * <p>That does not make a registered {@code rtsp://} or {@code srt://} URL harmless: MediaMTX
 * connects to whatever host and port it names each time the stream is played, so who may register
 * attachments is the guard against using it to reach internal hosts.
 */
public class RegisterMediaUrlUsecase implements RegisterMediaUrl {

  private static final Logger log = LoggerFactory.getLogger(RegisterMediaUrlUsecase.class);

  private final MediaUrlRepository mediaUrlRepository;
  private final StreamTransportPolicy streamTransportPolicy;

  public RegisterMediaUrlUsecase(
      MediaUrlRepository mediaUrlRepository, StreamTransportPolicy streamTransportPolicy) {
    this.mediaUrlRepository = mediaUrlRepository;
    this.streamTransportPolicy = streamTransportPolicy;
  }

  @Override
  public MediaUrlRecord register(
      UUID userId, String url, StreamKind requestedKind, MediaAccess access, MediaUsage usage) {
    if (url == null || url.isBlank()) {
      throw new GlobalOpenCopException("URL must not be blank", HttpStatus.BAD_REQUEST);
    }

    StreamKind kind;
    String storedUrl;
    try {
      var uri = new URI(url);
      if (!uri.isAbsolute()) {
        throw new GlobalOpenCopException("URL must be absolute", HttpStatus.BAD_REQUEST);
      }
      var scheme = uri.getScheme().toLowerCase(Locale.ROOT);
      if (uri.getHost() == null || uri.getHost().isBlank()) {
        throw new GlobalOpenCopException("URL must contain a host", HttpStatus.BAD_REQUEST);
      }
      // Reject credentials so they are not stored or sent to a connector.
      if (uri.getUserInfo() != null) {
        throw new GlobalOpenCopException(
            "URL must not contain credentials because they could be persisted or forwarded",
            HttpStatus.BAD_REQUEST);
      }

      kind = streamTransportPolicy.resolveKind(requestedKind, scheme, uri.getPath());
      streamTransportPolicy.assertQueryAllowed(kind, uri.getRawQuery());
      StreamTransportPolicy.assertConversionInputAllowed(kind, uri, url.length());
      // Stored with a lowercase scheme: the resolvers and the converter match it literally.
      storedUrl = scheme + url.substring(scheme.length());
    } catch (URISyntaxException e) {
      throw new GlobalOpenCopException("Invalid URL: " + e.getReason(), HttpStatus.BAD_REQUEST);
    }

    var mediaUrlId = UUID.randomUUID();
    var record =
        new MediaUrlRecord(
            mediaUrlId, storedUrl, kind, access, userId, userId, Instant.now(), usage);

    var saved = mediaUrlRepository.save(record);
    // No url: it may carry a signed/tokenized query string (e.g. an HLS auth token).
    log.debug(
        "URL media registered: id={}, streamKind={}, usage={}, userId={}",
        mediaUrlId,
        kind,
        usage,
        userId);
    return saved;
  }
}
