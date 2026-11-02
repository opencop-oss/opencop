// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.media;

import com.opencop.core.api.general.ports.usecase.media.MediaListFilter;
import com.opencop.core.api.general.ports.usecase.media.MediaPage;
import com.opencop.core.sdk.model.media.MediaRecord;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaRepository {

  MediaRecord save(MediaRecord record);

  Optional<MediaRecord> findById(UUID id);

  Optional<MediaRecord> findByIdForUpdate(UUID id);

  MediaPage list(int page, int size, MediaListFilter filter);

  void deleteById(UUID id);

  List<MediaRecord> findThumbnailsOf(UUID originalMediaId);

  boolean existsByBlobKeyExcludingIds(String blobKey, Collection<UUID> excludedIds);
}
