// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.export;

import com.opencop.core.api.general.business.export.ExportArtifact;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExportArtifactRepository {

  void save(ExportArtifact artifact);

  Optional<ExportArtifact> findById(UUID exportId);

  List<ExportArtifact> findExpiredBefore(OffsetDateTime expiresBefore);
}
