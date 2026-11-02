// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.runtimefeature;

import com.opencop.core.api.general.business.runtimefeature.RuntimeFeatureErrorCode;
import java.util.Objects;
import org.springframework.http.HttpStatus;

/**
 * Why the catalog refused a name, decided without committing to how the caller reports it.
 *
 * <p>A refusal reaches the caller two ways: the single-name route throws it, producing an error
 * response with {@link #status}; reconciliation records it against the name and still returns 200.
 * Deciding the refusal as a value is what keeps the second path honest — constructing a {@code
 * RuntimeFeatureException} logs the HTTP status it carries, so building one for a rejection that
 * comes back inside a 200 would announce a response the endpoint never sends.
 *
 * @param status the response status the single-name route returns for this reason
 */
public record RuntimeFeatureRejectionReason(
    RuntimeFeatureErrorCode code, String message, HttpStatus status) {

  public RuntimeFeatureRejectionReason {
    Objects.requireNonNull(code, "code is required");
    Objects.requireNonNull(message, "message is required");
    Objects.requireNonNull(status, "status is required");
  }
}
