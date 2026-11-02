// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.watchbox;

import tools.jackson.databind.JsonNode;

public record WatchboxFilterTestRequest(WatchboxFilter filter, JsonNode feature) {}
