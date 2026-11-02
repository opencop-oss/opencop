// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.input;

import java.util.List;
import java.util.Objects;

/**
 * Framework-neutral view of one TCP request used for extraction.
 *
 * @param values scalar values keyed by descriptor field name
 * @param resources uploaded resources keyed by descriptor field name in their role
 */
public record TcpConnectionInput(
    List<ConnectionInputValue> values, List<ConnectionInputResource> resources)
    implements ConnectionInput {

  public TcpConnectionInput {
    values = List.copyOf(Objects.requireNonNull(values, "values"));
    if (values.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("values must not contain null entries");
    }
    resources = List.copyOf(Objects.requireNonNull(resources, "resources"));
    if (resources.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("resources must not contain null values");
    }
  }

  /** The TCP view of a request's declared input. */
  public static TcpConnectionInput declared(ConnectionInput input) {
    Objects.requireNonNull(input, "input");
    return new TcpConnectionInput(input.values(), input.resources());
  }
}
