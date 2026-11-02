// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.ports.gateway;

import com.opencop.core.api.watchbox.business.model.WatchboxConfigAuditPage;
import com.opencop.core.api.watchbox.business.model.WatchboxConfigAuditQuery;

public interface WatchboxConfigAuditReader {

  WatchboxConfigAuditPage findPage(WatchboxConfigAuditQuery query);
}
