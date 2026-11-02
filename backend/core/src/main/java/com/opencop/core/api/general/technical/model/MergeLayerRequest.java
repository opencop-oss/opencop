// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model;

import java.util.List;
import java.util.UUID;

public record MergeLayerRequest(String name, List<UUID> layerIds) {}
