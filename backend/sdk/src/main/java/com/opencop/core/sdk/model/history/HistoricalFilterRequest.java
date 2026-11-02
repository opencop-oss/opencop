// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.history;

import java.util.Map;

public record HistoricalFilterRequest(String type, Map<String, Object> params) {}
