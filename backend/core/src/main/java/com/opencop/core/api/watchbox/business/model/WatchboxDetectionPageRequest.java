// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.business.model;

/**
 * Zero-based offset page of a fixed-order feed. The endpoint derives it from the resolved web
 * paging request after rejecting any sort, so business and gateway code never see the web type.
 */
public record WatchboxDetectionPageRequest(int page, int size) {
  public WatchboxDetectionPageRequest {
    if (page < 0) {
      throw new IllegalArgumentException("page must be at least 0");
    }
    if (size < 1) {
      throw new IllegalArgumentException("size must be at least 1");
    }
  }

  public long offset() {
    return (long) page * size;
  }
}
