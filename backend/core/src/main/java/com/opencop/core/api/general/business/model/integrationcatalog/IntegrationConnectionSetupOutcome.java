// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.integrationcatalog;

import java.util.Objects;

/** Result of deciding whether an integration schema version can describe its connection setup. */
public sealed interface IntegrationConnectionSetupOutcome {

  /** The integration is active, available and describes TCP connection setup. */
  record Described(IntegrationConnectionSetup setup) implements IntegrationConnectionSetupOutcome {

    public Described {
      Objects.requireNonNull(setup, "setup");
    }
  }

  /** The integration cannot be described, for a reason a client may be told. */
  record NotDescribable(IntegrationConnectionSetupRejection reason)
      implements IntegrationConnectionSetupOutcome {

    public NotDescribable {
      Objects.requireNonNull(reason, "reason");
    }
  }
}
