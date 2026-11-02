// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.usecase.media.AuthorizeStreamPlaybackUsecase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class StreamAuthorizationEndpointTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440142");

  private final AuthorizeStreamPlaybackUsecase usecase = mock(AuthorizeStreamPlaybackUsecase.class);
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final StreamAuthorizationEndpoint endpoint =
      new StreamAuthorizationEndpoint(usecase, new StreamAuthorizationTelemetry(registry));

  @AfterEach
  void tearDown() {
    registry.close();
  }

  @Test
  void authorize_whenUnauthenticated_returnsUnauthorizedAndRecordsReason() {
    var response = endpoint.authorize(null, "/streams/att_" + USER_ID + "/index.m3u8", "GET");

    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    assertCounter("denied", "unauthenticated");
  }

  @Test
  void authorize_whenUsecaseThrows_recordsUnexpectedErrorAndRethrows() {
    var principal = com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(USER_ID);
    when(usecase.authorize(principal, "/streams/att_" + USER_ID + "/index.m3u8", "GET"))
        .thenThrow(new IllegalStateException("repository unavailable"));

    assertThrows(
        IllegalStateException.class,
        () -> endpoint.authorize(principal, "/streams/att_" + USER_ID + "/index.m3u8", "GET"));

    assertCounter("denied", "unexpected_error");
  }

  private void assertCounter(String outcome, String reason) {
    var counter =
        registry
            .find(StreamAuthorizationTelemetry.STREAM_AUTHZ_TOTAL)
            .tags("outcome", outcome, "reason", reason)
            .counter();
    assertNotNull(counter);
    assertEquals(1.0d, counter.count());
  }
}
