// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.ports.gateway.repository;

import com.opencop.core.sdk.model.iam.layer.LayerDirectoryEntry;
import com.opencop.core.sdk.model.iam.layer.LayerDirectoryReadFilter;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import org.springframework.data.domain.Pageable;

/**
 * Reads a filtered, sorted page of the combined layer/layer-group directory, resolved server-side.
 */
public interface LayerDirectoryReader {

  PageResponse<LayerDirectoryEntry> getPage(LayerDirectoryReadFilter filter, Pageable pageable);
}
