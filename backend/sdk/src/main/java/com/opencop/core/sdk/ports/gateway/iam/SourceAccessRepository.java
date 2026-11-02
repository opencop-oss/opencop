// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.iam;

import com.opencop.core.sdk.model.iam.source.SourceRole;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import java.util.List;
import java.util.UUID;

public interface SourceAccessRepository extends AccessRepository<ServerRole> {

  List<SourceRole> userRoles(UUID userId);
}
