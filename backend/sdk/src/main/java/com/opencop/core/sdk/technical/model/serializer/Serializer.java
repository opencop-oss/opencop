// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.model.serializer;

public interface Serializer<I, O> {

  O serialize(I value);

  I deserialize(O serialized);
}
