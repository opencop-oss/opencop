// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.watchbox;

import com.opencop.core.sdk.model.watchbox.WatchboxAlertEvent;
import java.util.Objects;

/** One claimed unit of alert delivery work: the outbox row id plus the wire event it carries. */
public record WatchboxAlertOutboxRow(long id, WatchboxAlertEvent event) {

  public WatchboxAlertOutboxRow {
    Objects.requireNonNull(event, "event");
  }
}
