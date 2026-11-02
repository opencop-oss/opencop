// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common.filter;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.ContentCachingRequestWrapper;

public final class BoundedContentCachingRequestWrapper extends ContentCachingRequestWrapper {

  private final int cacheLimit;
  private boolean contentOverflowed;

  public BoundedContentCachingRequestWrapper(HttpServletRequest request, int cacheLimit) {
    super(request, cacheLimit);
    this.cacheLimit = cacheLimit;
  }

  @Override
  protected void handleContentOverflow(int contentCacheLimit) {
    contentOverflowed = true;
  }

  public boolean isContentOverflowed() {
    return contentOverflowed;
  }

  public int getCacheLimit() {
    return cacheLimit;
  }
}
