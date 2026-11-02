// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model.group;

import java.util.Set;
import java.util.UUID;

public record GroupMembersPatchRequest(Set<UUID> add, Set<UUID> remove) {}
