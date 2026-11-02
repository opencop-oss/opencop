// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.business.policy.rtsp;

import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputException;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputFailure;
import java.net.URI;
import java.util.Set;

/**
 * An RTSP connection's stream URL must name an RTSP host directly: credentials belong in Vault, not
 * in the URL, and query parameters are not accepted.
 */
public final class RtspUrlPolicy {

  /** The declared field an RTSP stream reads its URL from. */
  public static final String URL_FIELD = "url";

  public static final String MISSING_URL = "MISSING_RTSP_URL";
  public static final String INVALID_URL = "INVALID_RTSP_URL";
  public static final String UNSUPPORTED_SCHEME = "UNSUPPORTED_RTSP_SCHEME";
  public static final String MISSING_HOST = "MISSING_RTSP_HOST";
  public static final String URL_CREDENTIALS = "RTSP_URL_CREDENTIALS";
  public static final String URL_QUERY = "RTSP_URL_QUERY";

  private static final Set<String> VALID_SCHEMES = Set.of("rtsp", "rtsps");

  private RtspUrlPolicy() {}

  public static void requireValid(String url) throws ConnectionInputException {
    if (url == null || url.isBlank()) {
      throw rejection(MISSING_URL, "RTSP URL must not be blank");
    }
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException _) {
      throw rejection(INVALID_URL, "Invalid RTSP URL");
    }
    if (!VALID_SCHEMES.contains(uri.getScheme())) {
      throw rejection(UNSUPPORTED_SCHEME, "RTSP URL must use rtsp:// or rtsps:// scheme");
    }
    if (uri.getHost() == null || uri.getHost().isBlank()) {
      throw rejection(MISSING_HOST, "RTSP URL must contain a host");
    }
    if (uri.getUserInfo() != null) {
      throw rejection(URL_CREDENTIALS, "RTSP URL must not contain credentials — use Vault instead");
    }
    if (uri.getQuery() != null) {
      throw rejection(URL_QUERY, "RTSP URL must not contain query parameters");
    }
  }

  private static ConnectionInputException rejection(String detailCode, String message) {
    return ConnectionInputException.rejecting(
        URL_FIELD, ConnectionInputFailure.INVALID_INPUT, detailCode, message);
  }
}
