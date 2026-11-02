// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.user.UserPreference;
import com.opencop.core.api.general.business.model.user.UserPreferencePatch;
import com.opencop.core.api.general.business.usecase.map.MapTileAccessUsecase;
import com.opencop.core.api.general.ports.gateway.repository.UserPreferenceRepository;
import com.opencop.core.api.general.ports.usecase.media.RetrieveMedia;
import com.opencop.core.sdk.business.model.MeasurementSystem;
import com.opencop.core.sdk.business.model.TemperatureUnit;
import com.opencop.core.sdk.business.model.ZoomBehavior;
import com.opencop.core.sdk.component.security.TestPrincipals;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class UserPreferenceUsecaseTest {

  @Mock private UserPreferenceRepository repository;
  @Mock private RetrieveMedia retrieveMedia;

  @Mock private MapTileAccessUsecase tileAccess;

  private UserPreferenceUsecase usecase;

  @BeforeEach
  void setUp() {
    usecase = new UserPreferenceUsecase(repository, retrieveMedia, tileAccess);
  }

  @Test
  void patchPreferences_returnsOkWithPatchedPreferences() throws PersistenceException {
    var userId = UUID.randomUUID();
    var principal = principal(userId);
    var patch =
        new UserPreferencePatch(
            Optional.of("preferred-user"),
            Optional.of("fr"),
            Optional.empty(),
            Optional.empty(),
            Optional.of("America/New_York"),
            Optional.of(ZoomBehavior.MOUSE),
            Optional.empty(),
            Optional.empty(),
            null);
    var preference =
        new UserPreference(
            "preferred-user",
            "fr",
            MeasurementSystem.METRIC,
            TemperatureUnit.CELSIUS,
            "America/New_York",
            ZoomBehavior.MOUSE,
            null,
            null,
            null,
            null);
    when(repository.patch(userId, patch)).thenReturn(preference);

    var result = usecase.patch(patch, principal);

    assertEquals(preference, result);
    verify(repository).patch(userId, patch);
  }

  @Test
  void patchPreferences_whenPersistenceFails_throwsBadRequest() throws PersistenceException {
    var userId = UUID.randomUUID();
    var principal = principal(userId);
    var patch =
        new UserPreferencePatch(
            Optional.of("preferred-user"),
            Optional.of("fr"),
            Optional.empty(),
            Optional.empty(),
            Optional.of("America/New_York"),
            Optional.of(ZoomBehavior.CENTER),
            Optional.empty(),
            Optional.empty(),
            null);
    when(repository.patch(userId, patch)).thenThrow(new PersistenceException("Invalid"));

    var error = assertThrows(GlobalOpenCopException.class, () -> usecase.patch(patch, principal));

    assertEquals("Invalid", error.getMessage());
    assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
  }

  @Test
  void patchPreferences_whenUsernameAlreadyTaken_propagatesConflict() throws PersistenceException {
    var userId = UUID.randomUUID();
    var principal = principal(userId);
    var patch =
        new UserPreferencePatch(
            Optional.of("taken-user"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            null);
    when(repository.patch(userId, patch))
        .thenThrow(
            new GlobalOpenCopException("Username already taken: taken-user", HttpStatus.CONFLICT));

    var error = assertThrows(GlobalOpenCopException.class, () -> usecase.patch(patch, principal));

    assertEquals("Username already taken: taken-user", error.getMessage());
    assertEquals(HttpStatus.CONFLICT, error.getHttpStatus());
  }

  @Test
  void patchPreferences_whenTimezoneIsInvalid_throwsBadRequest() {
    var userId = UUID.randomUUID();
    var principal = principal(userId);
    var patch =
        new UserPreferencePatch(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of("+02:00"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            null);

    var error = assertThrows(GlobalOpenCopException.class, () -> usecase.patch(patch, principal));

    assertEquals("Invalid timezone: +02:00", error.getMessage());
    assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
  }

  @Test
  void patchPreferences_whenUsernameIsBlank_throwsBadRequest() {
    var userId = UUID.randomUUID();
    var principal = principal(userId);
    var patch =
        new UserPreferencePatch(
            Optional.of("   "),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            null);

    var error = assertThrows(GlobalOpenCopException.class, () -> usecase.patch(patch, principal));

    assertEquals("Invalid username: must not be blank", error.getMessage());
    assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
  }

  @Test
  void patch_whenDefaultTileIsNotUsable_throwsBadRequest() {
    var userId = UUID.randomUUID();
    var principal = principal(userId);
    var tileId = UUID.randomUUID();
    when(tileAccess.canUse(tileId, principal)).thenReturn(false);

    var error =
        assertThrows(
            GlobalOpenCopException.class,
            () -> usecase.patch(patchWithDefaultTile(tileId), principal));

    assertEquals("Default tile is not available to this user", error.getMessage());
    assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
  }

  @Test
  void patch_whenDefaultTileIsUsable_isStored() throws PersistenceException {
    var userId = UUID.randomUUID();
    var principal = principal(userId);
    var tileId = UUID.randomUUID();
    var patch = patchWithDefaultTile(tileId);
    when(tileAccess.canUse(tileId, principal)).thenReturn(true);
    when(repository.patch(userId, patch)).thenReturn(preferenceWithoutTile());

    usecase.patch(patch, principal);

    verify(repository).patch(userId, patch);
  }

  private static UserPreferencePatch patchWithDefaultTile(UUID tileId) {
    return new UserPreferencePatch(
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.of(tileId),
        null);
  }

  private static UserPreference preferenceWithoutTile() {
    return new UserPreference(
        "preferred-user",
        "en",
        MeasurementSystem.METRIC,
        TemperatureUnit.CELSIUS,
        "UTC",
        ZoomBehavior.MOUSE,
        null,
        null,
        null,
        null);
  }

  private static OpenCopPrincipal principal(UUID userId) {
    return TestPrincipals.mockPrincipal(userId);
  }
}
