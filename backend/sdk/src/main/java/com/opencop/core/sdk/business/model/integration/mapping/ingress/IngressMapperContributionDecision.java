// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.mapping.ingress;

import com.opencop.core.sdk.ports.gateway.mapping.ingress.PayloadIngressMapper;
import java.util.Objects;
import java.util.Optional;
import org.geojson.Feature;

/** The admission decision for one ingress mapper contribution. */
public record IngressMapperContributionDecision(
    PayloadIngressMapper<? extends Feature> contribution,
    Optional<RejectionReason> rejectionReason) {

  public IngressMapperContributionDecision {
    Objects.requireNonNull(contribution, "contribution");
    Objects.requireNonNull(rejectionReason, "rejectionReason");
  }

  public static IngressMapperContributionDecision admitted(
      PayloadIngressMapper<? extends Feature> contribution) {
    return new IngressMapperContributionDecision(contribution, Optional.empty());
  }

  public static IngressMapperContributionDecision rejected(
      PayloadIngressMapper<? extends Feature> contribution, RejectionReason reason) {
    return new IngressMapperContributionDecision(contribution, Optional.of(reason));
  }

  public boolean admitted() {
    return rejectionReason.isEmpty();
  }

  /** Stable business reasons for rejecting an ingress mapper contribution. */
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
