// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.audit;

import com.opencop.core.api.general.business.model.audit.AuditPage;
import com.opencop.core.api.general.business.model.audit.AuditQuery;
import com.opencop.core.api.general.ports.gateway.audit.AuditReader;
import com.opencop.core.api.general.ports.usecase.audit.ReadAuditHistory;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * What the ledger will answer, and what it refuses to.
 *
 * <p>The bounds live here rather than at the endpoint because they are properties of the read, not
 * of HTTP: a query past the offset range or with its time bounds crossed is unanswerable whoever
 * asks it.
 */
@Service
public class ReadAuditHistoryUsecase implements ReadAuditHistory {

  private final AuditReader reader;

  public ReadAuditHistoryUsecase(AuditReader reader) {
    this.reader = reader;
  }

  @Override
  public AuditPage read(AuditQuery query) {
    validate(query);
    return this.reader.findPage(query);
  }

  private static void validate(AuditQuery query) {
    if (query.page() < 0) {
      throw new GlobalOpenCopException("page must not be negative", HttpStatus.BAD_REQUEST);
    }
    if (query.size() < 1 || query.size() > AuditQuery.MAXIMUM_SIZE) {
      throw new GlobalOpenCopException(
          "size must be between 1 and " + AuditQuery.MAXIMUM_SIZE, HttpStatus.BAD_REQUEST);
    }
    // Spring Data computes the offset as an int, so a page past that range throws from inside the
    // repository and surfaces as a 500. The caller asked for something unanswerable, which is a
    // 400. Same guard as the runtime-feature and watchbox history endpoints.
    if ((long) query.page() * query.size() > Integer.MAX_VALUE) {
      throw new GlobalOpenCopException(
          "page and size exceed the supported range", HttpStatus.BAD_REQUEST);
    }
    if (query.occurredFrom() != null
        && query.occurredTo() != null
        && query.occurredFrom().isAfter(query.occurredTo())) {
      throw new GlobalOpenCopException(
          "occurredFrom must not be after occurredTo", HttpStatus.BAD_REQUEST);
    }
  }
}
