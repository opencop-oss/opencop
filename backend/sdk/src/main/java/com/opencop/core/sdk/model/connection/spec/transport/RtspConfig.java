// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.connection.spec.transport;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import org.geojson.GeoJsonObject;

/**
 * Per-source persisted RTSP configuration.
 *
 * <p>Only values that vary by data source belong here. Connector-wide runtime policy, such as
 * timeout and RTSP transport preference, lives in Connect config. Host and port are derived from
 * {@link #url()}, not duplicated into JSONB.
 */
@JsonTypeName("rtsp")
@JsonIgnoreProperties(ignoreUnknown = true)
public record RtspConfig(String url, GeoJsonObject coordinates) implements TransportConfig {

  public static final int DEFAULT_RTSP_PORT = 554;
  public static final int DEFAULT_RTSPS_PORT = 322;

  @Override
  @JsonIgnore
  public TransportKind kind() {
    return TransportKind.RTSP;
  }

  @Override
  @JsonIgnore
  public List<String> secretRefs() {
    return List.of();
  }

  public RtspConfig {
    var parsed = parse(url);
    validateScheme(parsed);
    validateHost(parsed);
    validateNoUserInfo(parsed);
  }

  @Override
  @JsonIgnore
  public String getHost() {
    return parse(url).getHost();
  }

  @Override
  @JsonIgnore
  public int getPort() {
    var parsed = parse(url);
    var explicitPort = parsed.getPort();
    if (explicitPort >= 0) {
      return explicitPort;
    }
    return "rtsps".equalsIgnoreCase(parsed.getScheme()) ? DEFAULT_RTSPS_PORT : DEFAULT_RTSP_PORT;
  }

  private static URI parse(String url) {
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("RTSP url is required");
    }
    try {
      return new URI(url);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Invalid RTSP url", e);
    }
  }

  private static void validateScheme(URI uri) {
    var scheme = uri.getScheme();
    if (scheme == null) {
      throw new IllegalArgumentException("RTSP url scheme is required");
    }
    var normalized = scheme.toLowerCase(Locale.ROOT);
    if (!normalized.equals("rtsp") && !normalized.equals("rtsps")) {
      throw new IllegalArgumentException("RTSP url must use rtsp or rtsps scheme");
    }
  }

  private static void validateHost(URI uri) {
    if (uri.getHost() == null || uri.getHost().isBlank()) {
      throw new IllegalArgumentException("RTSP url host is required");
    }
  }

  private static void validateNoUserInfo(URI uri) {
    if (uri.getUserInfo() != null) {
      throw new IllegalArgumentException("RTSP url userinfo is not supported");
    }
  }
}
