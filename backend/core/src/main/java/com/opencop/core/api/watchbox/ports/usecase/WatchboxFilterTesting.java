// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.ports.usecase;

import com.opencop.core.sdk.model.watchbox.WatchboxFilterMatch;
import com.opencop.core.sdk.model.watchbox.WatchboxFilterTestRequest;

public interface WatchboxFilterTesting {

  WatchboxFilterMatch test(WatchboxFilterTestRequest request);
}
