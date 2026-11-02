// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.media;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

final class AttachmentStreamPath {

  static final String PATH_PREFIX = "att_";

  private static final String UUID_PATTERN =
      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
  private static final Pattern FORWARDED_PATH =
      Pattern.compile("^/streams/" + PATH_PREFIX + "(" + UUID_PATTERN + ")/.+");

  private AttachmentStreamPath() {}

  static String pathName(UUID attachmentId) {
    return PATH_PREFIX + attachmentId;
  }

  static Optional<UUID> parseForwardedPath(String path) {
    var matcher = FORWARDED_PATH.matcher(path);
    if (!matcher.matches()) {
      return Optional.empty();
    }
    return Optional.of(UUID.fromString(matcher.group(1)));
  }
}
