// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.iam.source;

import java.util.Set;
import java.util.UUID;

public record AddNewSourceRequest(UUID serverId, Set<UUID> layers, Set<String> users) {}
