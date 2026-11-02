// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.me;

import com.opencop.core.sdk.model.iam.source.SourceInfo;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import java.util.List;
import java.util.UUID;

public interface RetrieveUserSources {

  List<SourceInfo> get(UUID userId, RecordVisibility recordVisibility);
}
