// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.mapping;

import java.util.Objects;
import java.util.UUID;

/**
 * One way an integration permits a TCP connection to pair the mapper it decodes with and the
 * framing that splits its stream into messages. Sealed, so every reader of a declaration handles
 * each rule.
 */
public sealed interface FramingRule
    permits MapperFramingPair, FramingRule.AnyMapperWith, FramingRule.AnyMapperCustomFraming {

  /**
   * Any available, eligible mapper may read a stream split by this framing.
   *
   * @param framingId a framing the integration provides in code
   */
  record AnyMapperWith(UUID framingId) implements FramingRule {

    public AnyMapperWith {
      Objects.requireNonNull(framingId, "framingId");
    }
  }

  /**
   * Any available, eligible mapper may read a stream the connection splits with a delimiter of its
   * own rather than one the integration provides.
   */
  record AnyMapperCustomFraming() implements FramingRule {}
}
