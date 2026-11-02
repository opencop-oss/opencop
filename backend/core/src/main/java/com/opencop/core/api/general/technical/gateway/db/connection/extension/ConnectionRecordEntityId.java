// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.connection.extension;

import java.io.Serializable;
import java.util.UUID;

/** The key of one record: its connection, extension, collection and key. */
public record ConnectionRecordEntityId(
    UUID sourceId, String extensionId, String collection, String recordKey)
    implements Serializable {}
