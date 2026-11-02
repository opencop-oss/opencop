// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.user;

import com.opencop.core.api.general.business.factory.auth.AdmittedOidcIdentityFactory;
import com.opencop.core.api.general.business.factory.user.CurrentAccountAccessFactory;
import com.opencop.core.api.general.business.model.auth.AdmittedOidcIdentity;
import com.opencop.core.api.general.business.model.auth.OidcAdmissionCommand;
import com.opencop.core.api.general.business.model.auth.OidcAdmissionDeniedException;
import com.opencop.core.api.general.business.policy.OidcAdmissionPolicy;
import com.opencop.core.api.general.ports.technical.gateway.UserService;
import com.opencop.core.sdk.business.model.account.CurrentAccountAccessDeniedException;
import com.opencop.core.sdk.business.policy.CurrentAccountAccessPolicy;
import com.opencop.core.sdk.ports.gateway.idp.IdpException;

public class OidcAdmissionUsecase {

  private final OidcAdmissionPolicy admissionPolicy;
  private final UserService userService;
  private final CurrentAccountAccessFactory currentAccountAccessFactory;
  private final CurrentAccountAccessPolicy currentAccountAccessPolicy;
  private final AdmittedOidcIdentityFactory admittedIdentityFactory;

  public OidcAdmissionUsecase(
      OidcAdmissionPolicy admissionPolicy,
      UserService userService,
      CurrentAccountAccessFactory currentAccountAccessFactory,
      CurrentAccountAccessPolicy currentAccountAccessPolicy,
      AdmittedOidcIdentityFactory admittedIdentityFactory) {
    this.admissionPolicy = admissionPolicy;
    this.userService = userService;
    this.currentAccountAccessFactory = currentAccountAccessFactory;
    this.currentAccountAccessPolicy = currentAccountAccessPolicy;
    this.admittedIdentityFactory = admittedIdentityFactory;
  }

  public AdmittedOidcIdentity admit(OidcAdmissionCommand command) {
    admissionPolicy.requireAllowed(command);
    try {
      var accountAccess =
          currentAccountAccessFactory.create(
              userService.get(command.idpUserId(), command.issuer()));
      currentAccountAccessPolicy.requireActive(accountAccess);
      return admittedIdentityFactory.create(command, accountAccess);
    } catch (IdpException | CurrentAccountAccessDeniedException exception) {
      throw new OidcAdmissionDeniedException(exception);
    }
  }
}
