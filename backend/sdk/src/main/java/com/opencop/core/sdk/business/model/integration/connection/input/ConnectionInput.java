// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.input;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Framework-neutral view of the input one connection request supplied for its extractor's declared
 * fields: scalar values for text and secret kinds, and uploaded resources for file kinds.
 *
 * <p>The host passes an extractor only what its own descriptor declares: a request with a value or
 * file under any other name, or under a field of the other kind, is rejected before the extractor
 * sees it. An extractor therefore never needs to check for undeclared input itself.
 */
public interface ConnectionInput {

  /** Scalar values keyed by descriptor field name. */
  List<ConnectionInputValue> values();

  /** Uploaded resources keyed by descriptor field name in their role. */
  List<ConnectionInputResource> resources();

  default List<ConnectionInputResource> resources(String role) {
    Objects.requireNonNull(role, "role");
    return resources().stream().filter(resource -> role.equals(resource.role())).toList();
  }

  /** Returns the value supplied under the descriptor field name, if any. */
  default Optional<String> value(String name) {
    Objects.requireNonNull(name, "name");
    return values().stream()
        .filter(supplied -> name.equals(supplied.name()))
        .map(ConnectionInputValue::value)
        .findFirst();
  }

  default boolean hasValue(String name) {
    return values().stream().anyMatch(supplied -> name.equals(supplied.name()));
  }
}
