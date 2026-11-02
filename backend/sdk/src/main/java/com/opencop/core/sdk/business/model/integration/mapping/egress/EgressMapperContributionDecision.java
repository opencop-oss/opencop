// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.mapping.egress;

import com.opencop.core.sdk.ports.gateway.mapping.egress.PayloadEgressMapper;
import java.util.Objects;
import java.util.Optional;

/** The admission decision for one egress mapper contribution. */
public record EgressMapperContributionDecision(
    PayloadEgressMapper contribution, Optional<RejectionReason> rejectionReason) {

  public EgressMapperContributionDecision {
    Objects.requireNonNull(contribution, "contribution");
    Objects.requireNonNull(rejectionReason, "rejectionReason");
  }

  public static EgressMapperContributionDecision admitted(PayloadEgressMapper contribution) {
    return new EgressMapperContributionDecision(contribution, Optional.empty());
  }

  public static EgressMapperContributionDecision rejected(
      PayloadEgressMapper contribution, RejectionReason reason) {
    return new EgressMapperContributionDecision(contribution, Optional.of(reason));
  }

  public boolean admitted() {
    return rejectionReason.isEmpty();
  }

  /** Stable business reasons for rejecting an egress mapper contribution. */
  public enum RejectionReason {
    MISSING_UUID("missing-uuid"),
    BLANK_NAME("blank-name"),
    NON_POSITIVE_VERSION("non-positive-version"),
    DUPLICATE_UUID("duplicate-uuid");

    private final String code;

    RejectionReason(String code) {
      this.code = code;
    }

    public String code() {
      return code;
    }
  }
}
