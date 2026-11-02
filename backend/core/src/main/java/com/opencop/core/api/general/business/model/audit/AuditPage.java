// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.audit;

import java.util.List;

/**
 * One page of history plus the exact total for the same filters.
 *
 * <p>Content and count are separate statements under Read Committed, so a concurrent insert can
 * leave the total slightly ahead of what this page holds.
 */
public record AuditPage(List<AuditEntry> content, long totalElements) {

  public AuditPage {
    content = List.copyOf(content);
  }
}
