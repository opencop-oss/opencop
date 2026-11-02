// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.audit;

import com.opencop.core.api.general.business.model.audit.AuditPage;
import com.opencop.core.api.general.business.model.audit.AuditQuery;

/** Reads the ledger. Separate from {@code AuditRecorder} so a reader cannot write. */
public interface AuditReader {

  AuditPage findPage(AuditQuery query);
}
