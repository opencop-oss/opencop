// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.cache;

public interface CachePublisher<T> {

  void publish(T event);
}
