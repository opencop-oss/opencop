// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.factory.auth.AdmittedOidcIdentityFactory;
import com.opencop.core.api.general.business.factory.user.CurrentAccountAccessFactory;
import com.opencop.core.api.general.business.model.auth.IdpRelationship;
import com.opencop.core.api.general.business.model.auth.OidcAdmissionCommand;
import com.opencop.core.api.general.business.model.auth.OidcAdmissionDeniedException;
import com.opencop.core.api.general.business.model.auth.OidcProfile;
import com.opencop.core.api.general.business.model.user.InternalUser;
import com.opencop.core.api.general.business.policy.OidcAdmissionPolicy;
import com.opencop.core.api.general.ports.technical.gateway.UserService;
import com.opencop.core.sdk.business.policy.CurrentAccountAccessPolicy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OidcAdmissionUsecaseTest {

  private static final UUID USER_ID = UUID.fromString("8d065639-90b3-4991-adac-8dc497f13b35");
  private static final Instant AUTHENTICATED_AT = Instant.parse("2026-07-23T15:00:00Z");
  private static final OidcProfile PROFILE =
      new OidcProfile("preferred", "Profile Name", "Profile", "Name", "profile@example.test", true);

  private final UserService userService = mock(UserService.class);

  @Test
  void successfulAdmissionUsesOneAccountSnapshotAndServerClock() throws Exception {
    when(userService.get("verified-subject", "https://issuer.test"))
        .thenReturn(internalUser(true, 17));
    var usecase =
        usecase(
            new OidcAdmissionPolicy(List.of(new IdpRelationship("group-a", List.of("operator")))));

    var admitted =
        usecase.admit(
            command(
                List.of("CORE-API"), List.of("group-a", "group-b"), List.of("operator"), PROFILE));

    assertThat(admitted.internalUserId()).isEqualTo(USER_ID);
    assertThat(admitted.issuer()).isEqualTo("https://issuer.test");
    assertThat(admitted.subject()).isEqualTo("verified-subject");
    assertThat(admitted.idpUserId()).isEqualTo("verified-subject");
    assertThat(admitted.profile()).isEqualTo(PROFILE);
    assertThat(admitted.sessionGeneration()).isEqualTo(17);
    assertThat(admitted.authenticatedAt()).isEqualTo(AUTHENTICATED_AT);
    verify(userService).get("verified-subject", "https://issuer.test");
  }

  @Test
  void deniedRelationshipStopsBeforeAccountLookup() throws Exception {
    var usecase =
        usecase(new OidcAdmissionPolicy(List.of(new IdpRelationship("required-group", List.of()))));

    assertThrows(
        OidcAdmissionDeniedException.class,
        () ->
            usecase.admit(
                command(List.of("CORE-API"), List.of("other-group"), List.of(), PROFILE)));

    verify(userService, never()).get("verified-subject", "https://issuer.test");
  }

  @Test
  void inactiveProvisionedAccountIsDeniedAfterSingleLookup() throws Exception {
    when(userService.get("verified-subject", "https://issuer.test"))
        .thenReturn(internalUser(false, 17));
    var usecase = usecase(new OidcAdmissionPolicy(List.of()));

    assertThrows(
        OidcAdmissionDeniedException.class,
        () -> usecase.admit(command(List.of(), List.of(), List.of(), PROFILE)));

    verify(userService).get("verified-subject", "https://issuer.test");
  }

  private OidcAdmissionUsecase usecase(OidcAdmissionPolicy admissionPolicy) {
    return new OidcAdmissionUsecase(
        admissionPolicy,
        userService,
        new CurrentAccountAccessFactory(),
        new CurrentAccountAccessPolicy(),
        new AdmittedOidcIdentityFactory(Clock.fixed(AUTHENTICATED_AT, ZoneOffset.UTC)));
  }

  private static OidcAdmissionCommand command(
      List<String> audiences, List<String> groups, List<String> roles, OidcProfile profile) {
    return new OidcAdmissionCommand(
        "https://issuer.test",
        "verified-subject",
        "verified-subject",
        audiences,
        groups,
        roles,
        profile);
  }

  private static InternalUser internalUser(boolean active, long sessionGeneration) {
    var now = Instant.parse("2026-07-22T12:00:00Z");
    return new InternalUser(
        USER_ID,
        "verified-subject",
        "idp-username",
        "https://issuer.test",
        "username",
        "profile@example.test",
        "Profile",
        "Name",
        "Profile Name",
        active,
        true,
        sessionGeneration,
        4,
        now,
        now,
        null);
  }
}
