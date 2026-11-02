// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.datasource;

/**
 * Combines data source metadata (from DB) with runtime status (from Connect).
 *
 * <p>This record is returned by DataSourceRuntimeService which fetches both in bulk and handles
 * caching.
 */
public record DataSourceRuntime(DataSource dataSource, RuntimeStatus runtime) {

  /** Convenience accessor for the data source name. */
  public String name() {
    return dataSource.name();
  }
}
