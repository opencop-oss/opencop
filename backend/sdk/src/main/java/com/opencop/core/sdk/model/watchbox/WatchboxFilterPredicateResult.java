// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.watchbox;

public record WatchboxFilterPredicateResult(
    int index, String path, WatchboxFilterOperation operation, boolean negate, boolean matched) {}
