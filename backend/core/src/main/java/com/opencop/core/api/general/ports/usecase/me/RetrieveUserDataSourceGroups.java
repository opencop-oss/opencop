// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.me;

import com.opencop.core.api.general.technical.model.datasourcegroup.MyDataSourceGroupResponse;
import java.util.List;
import java.util.UUID;

public interface RetrieveUserDataSourceGroups {

  List<MyDataSourceGroupResponse> get(UUID userId);
}
