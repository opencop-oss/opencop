// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.media;

public interface ListMedia {

  MediaPage list(int page, int size, MediaListFilter filter);
}
