// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.input;

import java.util.List;
import java.util.Objects;

/**
 * The input a request supplied for its extractor's declared fields, and nothing else.
 *
 * @param values scalar values keyed by descriptor field name
 * @param resources uploaded resources keyed by descriptor field name in their role
 */
public record DeclaredConnectionInput(
    List<ConnectionInputValue> values, List<ConnectionInputResource> resources)
    implements ConnectionInput {

  public DeclaredConnectionInput {
    values = List.copyOf(Objects.requireNonNull(values, "values"));
    resources = List.copyOf(Objects.requireNonNull(resources, "resources"));
  }
}
