// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.technical.gateway.db;

import com.opencop.core.sdk.ports.gateway.repository.RepositoryException;
import java.util.List;

public interface GeoJsonRepository<I> {

  void batchPersist(List<I> input) throws RepositoryException;
}
