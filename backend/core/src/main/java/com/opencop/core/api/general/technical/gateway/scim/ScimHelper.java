// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.scim;

import com.opencop.core.api.general.technical.endpoint.scim.ScimExceptions;
import com.unboundid.scim2.common.Path;
import com.unboundid.scim2.common.messages.PatchRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ScimHelper {

  static String normalizePath(Path path) {
    if (path == null) {
      return null;
    }
    String trimmed = path.toString().trim();
    return trimmed.isBlank() ? null : trimmed;
  }

  static void validatePatch(PatchRequest patch) {
    if (patch == null || patch.getOperations() == null || patch.getOperations().isEmpty()) {
      throw new ScimExceptions.ScimBadRequestException("PatchRequest must contain Operations.");
    }
  }

  // Supported format: <attr> eq <value> (or <attr> eq <value>)*
  static final Pattern ATOM =
      Pattern.compile(
          "^\\s*([\\w.]+)\\s+eq\\s+(?:\"([^\"]*)\"|'([^']*)'|(\\S+))\\s*$",
          Pattern.CASE_INSENSITIVE);

  static Optional<MultiEqFilter> parseFilterSafe(String filter) {
    if (filter == null || filter.isBlank()) {
      return Optional.empty();
    }

    String[] parts = filter.split("(?i)\\s+or\\s+");
    String field0 = null;
    List<String> values = new ArrayList<>();

    for (String part : parts) {
      Matcher matcher = ATOM.matcher(part);
      if (!matcher.matches()) {
        return Optional.empty();
      }

      String field = matcher.group(1);
      String value =
          matcher.group(2) != null
              ? matcher.group(2)
              : matcher.group(3) != null ? matcher.group(3) : matcher.group(4);

      if (field0 == null) {
        field0 = field;
      } else if (!field0.equalsIgnoreCase(field)) {
        return Optional.empty();
      }

      values.add(value);
    }

    if (field0 == null) {
      return Optional.empty();
    }

    return Optional.of(new MultiEqFilter(field0, List.copyOf(values)));
  }

  public record MultiEqFilter(String field, List<String> values) {}
}
