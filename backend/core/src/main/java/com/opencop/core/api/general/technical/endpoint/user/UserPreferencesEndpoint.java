// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.user;

import com.opencop.core.api.general.business.model.user.UserPreference;
import com.opencop.core.api.general.business.model.user.UserPreferencePatch;
import com.opencop.core.api.general.business.usecase.user.UserPreferenceUsecase;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserPreferencesEndpoint {

  private final UserPreferenceUsecase usecase;

  UserPreferencesEndpoint(UserPreferenceUsecase usecase) {
    this.usecase = usecase;
  }

  @GetMapping("/v1/me/preferences")
  public ResponseEntity<UserPreference> getPreferences(
      @AuthenticationPrincipal OpenCopPrincipal authentication) {
    return ResponseEntity.ok(usecase.get(authentication));
  }

  @PatchMapping("/v1/me/preferences")
  public ResponseEntity<UserPreference> patchPreferences(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @RequestBody UserPreferencePatch body) {
    return ResponseEntity.ok(usecase.patch(body, authentication));
  }
}
