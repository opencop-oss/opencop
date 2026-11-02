// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection;

import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputResource;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputValue;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorField;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorFieldKind;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A request supplies an extractor only the fields its descriptor declares: each value under a
 * declared text or secret field, each file under a declared file or file-list field. An extractor
 * reads the fields it knows and skips the rest, so without this a misspelt or misplaced field would
 * be dropped silently rather than reported.
 *
 * <p>Only names and kinds are checked. Whether a value or file is valid stays the extractor's
 * decision.
 */
public final class DeclaredConnectionInputPolicy {

  public static final String UNDECLARED_FIELD = "UNDECLARED_INPUT_FIELD";

  private static final Set<ExtractorFieldKind> VALUE_KINDS =
      Set.of(ExtractorFieldKind.TEXT, ExtractorFieldKind.SECRET);
  private static final Set<ExtractorFieldKind> FILE_KINDS =
      Set.of(ExtractorFieldKind.FILE, ExtractorFieldKind.FILE_LIST);

  private DeclaredConnectionInputPolicy() {}

  /** Rejects the first value or file the descriptor does not declare with a matching kind. */
  public static void requireDeclared(ExtractorDescriptor descriptor, ConnectionInput input) {
    var valueFields = namesOf(descriptor, VALUE_KINDS);
    var fileFields = namesOf(descriptor, FILE_KINDS);
    input.values().stream()
        .map(ConnectionInputValue::name)
        .filter(name -> !valueFields.contains(name))
        .findFirst()
        .ifPresent(
            name -> {
              throw undeclared(
                  name, "This extractor declares no text or secret field by this name");
            });
    input.resources().stream()
        .map(ConnectionInputResource::role)
        .filter(role -> !fileFields.contains(role))
        .findFirst()
        .ifPresent(
            role -> {
              throw undeclared(role, "This extractor declares no file field by this name");
            });
  }

  private static Set<String> namesOf(
      ExtractorDescriptor descriptor, Set<ExtractorFieldKind> kinds) {
    return descriptor.fields().stream()
        .filter(field -> kinds.contains(field.kind()))
        .map(ExtractorField::name)
        .collect(Collectors.toUnmodifiableSet());
  }

  private static ConnectionManagementException undeclared(String field, String message) {
    return ConnectionManagementException.inputRejected(UNDECLARED_FIELD, field, message);
  }
}
