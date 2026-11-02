// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.idp;

import com.opencop.core.api.general.business.model.auth.AdmittedOidcIdentity;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;

public class MinimalPrincipalFactory {

  public OpenCopPrincipal create(AdmittedOidcIdentity identity) {
    var profile = identity.profile();
    var principalProfile =
        profile == null
            ? new OpenCopPrincipal.Profile(null, null, null, null, null, null)
            : new OpenCopPrincipal.Profile(
                profile.preferredUsername(),
                profile.fullName(),
                profile.givenName(),
                profile.familyName(),
                profile.email(),
                profile.emailVerified());
    return new OpenCopPrincipal(
        identity.internalUserId(),
        identity.issuer(),
        identity.idpUserId(),
        identity.subject(),
        principalProfile,
        identity.sessionGeneration(),
        identity.authenticatedAt());
  }
}
