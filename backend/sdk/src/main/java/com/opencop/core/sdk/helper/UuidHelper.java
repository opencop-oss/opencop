// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.helper;

import java.util.UUID;
import java.util.regex.Pattern;

public final class UuidHelper {

  public static final Pattern SAME_CHAR_UUID =
      Pattern.compile("^([0-9a-fA-F])\\1{7}-\\1{4}-\\1{4}-\\1{4}-\\1{12}$");

  public static boolean isSameCharUuid(UUID uuid) {
    if (uuid == null) {
      return false;
    }
    return SAME_CHAR_UUID.matcher(uuid.toString()).matches();
  }
}
