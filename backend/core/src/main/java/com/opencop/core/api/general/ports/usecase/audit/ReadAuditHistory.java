// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.audit;

import com.opencop.core.api.general.business.model.audit.AuditPage;
import com.opencop.core.api.general.business.model.audit.AuditQuery;

/** Reads a page of the shared resource audit ledger. */
public interface ReadAuditHistory {

  /**
   * @param query the filters, page and size as the caller asked for them, validated here rather
   *     than at the endpoint so every caller is held to the same bounds
   * @throws com.opencop.core.sdk.ports.exception.GlobalOpenCopException with 400 when the query
   *     cannot be answered
   */
  AuditPage read(AuditQuery query);
}
