// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.runtimefeature;

import java.util.List;

/**
 * Per-name outcome of a reconciliation.
 *
 * <p>Always returned with 200, including when some names were rejected: the operation itself
 * succeeded in doing everything it could, and the body is the report. A non-2xx here would either
 * hide the names that did register or force a client to parse an error body to find them.
 *
 * @param created definitions this request inserted
 * @param alreadyRegistered definitions that already existed, returned as stored
 * @param rejected names that could not be registered, each with its reason
 * @param complete true when nothing was rejected
 */
public record RuntimeFeatureReconcileResponse(
    List<RuntimeFeatureRecordResponse> created,
    List<RuntimeFeatureRecordResponse> alreadyRegistered,
    List<Rejection> rejected,
    boolean complete) {

  public RuntimeFeatureReconcileResponse {
    created = List.copyOf(created);
    alreadyRegistered = List.copyOf(alreadyRegistered);
    rejected = List.copyOf(rejected);
  }

  /**
   * @param code the same error code the single-name POST returns for this failure, so one client
   *     table classifies both paths
   */
  public record Rejection(String name, String code, String message) {}
}
