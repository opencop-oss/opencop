// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.session;

import com.opencop.core.sdk.model.mapper.rules.MappingRule;

public record SessionField(MappingRule rule, Object value) {}
