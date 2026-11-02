// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.repository;

import com.opencop.core.api.general.business.model.user.UserPreference;
import com.opencop.core.api.general.business.model.user.UserPreferencePatch;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import java.util.UUID;

public interface UserPreferenceRepository {

  UserPreference findByUserId(UUID userId);

  UserPreference create(UUID userId);

  UserPreference patch(UUID userId, UserPreferencePatch patch) throws PersistenceException;
}
