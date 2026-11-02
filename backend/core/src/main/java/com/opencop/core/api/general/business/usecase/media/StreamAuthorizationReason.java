// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.media;

public enum StreamAuthorizationReason {
  ALLOWED("allowed"),
  UNAUTHENTICATED("unauthenticated"),
  MISSING_FORWARDED_URI("missing_forwarded_uri"),
  MALFORMED_STREAM_PATH("malformed_stream_path"),
  UNSUPPORTED_METHOD("unsupported_method"),
  MEDIAMTX_DISABLED("mediamtx_disabled"),
  ATTACHMENT_NOT_FOUND("attachment_not_found"),
  ATTACHMENT_NOT_STREAMABLE("attachment_not_streamable"),
  NO_ACCESSIBLE_ASSOCIATION("no_accessible_association"),
  UNEXPECTED_ERROR("unexpected_error");

  private final String code;

  StreamAuthorizationReason(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }
}
