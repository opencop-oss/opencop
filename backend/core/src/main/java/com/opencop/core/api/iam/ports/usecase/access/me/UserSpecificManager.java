// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.ports.usecase.access.me;

import com.opencop.core.sdk.model.iam.source.LayerInfo;
import com.opencop.core.sdk.model.iam.source.SourceRole;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import java.util.List;
import java.util.UUID;

public interface UserSpecificManager {

  List<SourceRole> getLayerInfo(UUID userId, UUID layerId) throws UserSpecificException;

  List<LayerInfo> getUserLayers(UUID userId) throws UserSpecificException;

  List<LayerInfo> getUserLayers(UUID userId, RecordVisibility recordVisibility)
      throws UserSpecificException;

  List<SourceRole> getUserServers(UUID userId) throws UserSpecificException;

  List<SourceRole> getUserDirectServers(UUID userId) throws UserSpecificException;
}
