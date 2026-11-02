// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.user;

import com.opencop.core.api.general.business.model.auth.MeResponse;
import com.opencop.core.api.general.business.usecase.user.RetrieveMeUsecase;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeEndpoint {

  private final RetrieveMeUsecase usecase;

  MeEndpoint(RetrieveMeUsecase usecase) {
    this.usecase = usecase;
  }

  @GetMapping("/v1/me")
  public ResponseEntity<MeResponse> me(
      Authentication authentication, @AuthenticationPrincipal OpenCopPrincipal principal) {

    if (authentication == null || !authentication.isAuthenticated()) {
      return ResponseEntity.status(401).body(null);
    }

    return ResponseEntity.ok(usecase.get(authentication, principal));
  }
}
