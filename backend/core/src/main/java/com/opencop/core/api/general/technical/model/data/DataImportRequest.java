// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model.data;

import java.util.UUID;

public record DataImportRequest(
    UUID mapperId, byte[] payload, String filename, String contentType) {}
