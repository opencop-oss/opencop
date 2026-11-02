// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.runtimefeature;

import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureState;
import java.util.List;
import java.util.Optional;

public interface RuntimeFeatureStateReader {
  List<RuntimeFeatureState> findAll();

  Optional<RuntimeFeatureState> findByName(String name);
}
