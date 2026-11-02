// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.history.chunk;

import com.opencop.processor.business.model.history.SourceObjectKey;
import opencop.core.proto.deckgl.ElementProtos.ElementMessage;

record HistoricalChunkPointPayload(
    SourceObjectKey sourceObjectKey,
    String objectId,
    ElementMessage message,
    int serializedSizeBytes) {}
