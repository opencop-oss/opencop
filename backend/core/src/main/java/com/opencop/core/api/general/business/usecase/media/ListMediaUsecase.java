// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.media;

import com.opencop.core.api.general.ports.gateway.media.MediaRepository;
import com.opencop.core.api.general.ports.usecase.media.ListMedia;
import com.opencop.core.api.general.ports.usecase.media.MediaListFilter;
import com.opencop.core.api.general.ports.usecase.media.MediaPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListMediaUsecase implements ListMedia {

  private static final Logger log = LoggerFactory.getLogger(ListMediaUsecase.class);

  private final MediaRepository mediaRepository;

  public ListMediaUsecase(MediaRepository mediaRepository) {
    this.mediaRepository = mediaRepository;
  }

  @Override
  public MediaPage list(int page, int size, MediaListFilter filter) {
    var result = mediaRepository.list(page, size, filter);
    log.debug(
        "Media list: page={}, size={}, filter={}, totalCount={}",
        page,
        size,
        filter,
        result.totalCount());
    return result;
  }
}
