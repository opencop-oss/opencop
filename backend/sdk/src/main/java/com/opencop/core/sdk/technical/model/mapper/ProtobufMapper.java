// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.model.mapper;

import com.google.protobuf.GeneratedMessage;

public interface ProtobufMapper<I, O extends GeneratedMessage> {
  O toMessage(I input);

  I fromMessage(O message);
}
