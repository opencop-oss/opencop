// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.mapper;

import java.util.UUID;

public interface EventMapper<I, O> {

  O toOpenCopModel(UUID serverId, I rawInput);

  I fromOpenCopModel(O opencopModel);
}
