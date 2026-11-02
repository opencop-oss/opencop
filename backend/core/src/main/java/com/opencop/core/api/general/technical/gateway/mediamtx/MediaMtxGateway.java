// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.mediamtx;

import com.opencop.core.api.general.ports.gateway.media.MediaStreamGateway;
import com.opencop.core.api.general.technical.config.MediaMtxProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Outbound gateway for the MediaMTX REST API (v3). */
public class MediaMtxGateway implements MediaStreamGateway {

  private static final Logger log = LoggerFactory.getLogger(MediaMtxGateway.class);
  private static final String MEDIAMTX_STREAM_PATH_PREFIX = "streams/";

  private final MediaMtxProperties props;
  private final HttpClient http;

  public MediaMtxGateway(MediaMtxProperties props) {
    this.props = props;
    this.http = HttpClient.newBuilder().connectTimeout(props.getApi().getConnectTimeout()).build();
  }

  /**
   * Registers (or replaces) a MediaMTX on-demand path for the given RTSP or SRT stream, then
   * returns the HLS playlist URL for it. Uses the {@code replace} endpoint so the call is
   * idempotent — safe to call on every user request.
   *
   * <p>Returns empty if the HTTP call to MediaMTX fails; the caller should treat this as a
   * temporary unavailability rather than a hard error.
   */
  @Override
  public Optional<String> prepareStream(String pathName, String sourceUrl) {
    var publicHlsBaseUrl = publicHlsBaseUrl();
    if (publicHlsBaseUrl.isEmpty()) {
      log.warn("MediaMTX stream preparation unavailable: public HLS base URL is not configured");
      return Optional.empty();
    }

    var b64Url = Base64.getEncoder().encodeToString(sourceUrl.getBytes(StandardCharsets.UTF_8));
    var body =
        """
        {
          "source": "publisher",
          "runOnDemand": "/usr/local/bin/opencop-mediamtx-ffmpeg-adapter %s",
          "runOnDemandStartTimeout": "90s",
          "runOnDemandCloseAfter": "60s",
          "runOnDemandRestart": false
        }
        """
            .formatted(b64Url);

    var url =
        props.getApi().getBaseUrl() + "/v3/config/paths/replace/" + mediaMtxPathName(pathName);
    var request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(props.getApi().getReadTimeout())
            .header("Authorization", basicAuth())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    try {
      var response = http.send(request, HttpResponse.BodyHandlers.discarding());
      if (response.statusCode() == 200) {
        log.debug("MediaMTX path registered");
        return Optional.of(publicHlsBaseUrl.get() + "/" + pathName + "/index.m3u8");
      }
      log.warn("MediaMTX path registration returned {}", response.statusCode());
      return Optional.empty();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("MediaMTX path registration interrupted", e);
    } catch (IOException e) {
      log.warn("MediaMTX path registration failed: {}", e.getMessage());
      return Optional.empty();
    }
  }

  private String basicAuth() {
    var credentials = props.getApi().getUsername() + ":" + props.getApi().getPassword();
    return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
  }

  private static String mediaMtxPathName(String pathName) {
    return MEDIAMTX_STREAM_PATH_PREFIX + pathName;
  }

  private Optional<String> publicHlsBaseUrl() {
    var publicBaseUrl = props.getHls().getPublicBaseUrl();
    if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(publicBaseUrl.stripTrailing().replaceAll("/$", ""));
  }
}
