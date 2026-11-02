// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import com.opencop.core.sdk.ports.gateway.repository.RepositoryException;
import com.opencop.processor.technical.gateway.model.PostGisFeatureRecord;
import java.util.List;

public interface PostGisFeatureRecordWriter {

  /** Without a batch identity, so no resource activity is recorded. */
  default void save(List<PostGisFeatureRecord> featureRecords) throws RepositoryException {
    save(featureRecords, null);
  }

  /**
   * Persists a batch and records the resource activity it represents.
   *
   * @param batchKey stable identity of this batch, used so a redelivery is recorded once. Null for
   *     entry points with no batch identity, which then record no activity rather than inventing
   *     one and counting the same features repeatedly.
   */
  void save(List<PostGisFeatureRecord> featureRecords, String batchKey) throws RepositoryException;
}
