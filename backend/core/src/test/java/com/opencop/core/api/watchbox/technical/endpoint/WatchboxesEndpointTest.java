// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.technical.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.watchbox.ports.usecase.ManageWatchboxes;
import com.opencop.core.api.watchbox.ports.usecase.WatchboxCaller;
import com.opencop.core.api.watchbox.ports.usecase.WatchboxFilterTesting;
import com.opencop.core.sdk.component.security.TestPrincipals;
import com.opencop.core.sdk.model.watchbox.WatchboxCapabilities;
import com.opencop.core.sdk.model.watchbox.WatchboxCreateRequest;
import com.opencop.core.sdk.model.watchbox.WatchboxDirection;
import com.opencop.core.sdk.model.watchbox.WatchboxFilterMatch;
import com.opencop.core.sdk.model.watchbox.WatchboxPatchRequest;
import com.opencop.core.sdk.model.watchbox.WatchboxResponse;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

class WatchboxesEndpointTest {

  private final ManageWatchboxes usecase = mock(ManageWatchboxes.class);
  private final WatchboxFilterTesting filterTesting = mock(WatchboxFilterTesting.class);
  private final WatchboxesEndpoint endpoint =
      new WatchboxesEndpoint(usecase, filterTesting, new ObjectMapper());

  @Test
  void createReturnsCreated() {
    var principal = TestPrincipals.oidcPrincipal(UUID.randomUUID());
    var request = mock(WatchboxCreateRequest.class);

    var response = endpoint.create(request, principal);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    verify(usecase)
        .create(request, new WatchboxCaller(principal.internalUserId(), principal.isGlobalAdmin()));
  }

  @Test
  void actorIdentity_whenBuilt_usesDirectFullNameWithoutPreferredUsernameFallback() {
    var userId = UUID.randomUUID();
    var principal =
        new OpenCopPrincipal(
            userId,
            "http://localhost/test-issuer",
            "idp-user",
            "subject",
            new OpenCopPrincipal.Profile(
                "preferred-user", null, null, null, "actor@example.test", true),
            0,
            Instant.EPOCH);
    var request = mock(WatchboxCreateRequest.class);

    endpoint.create(request, principal);

    var caller = ArgumentCaptor.forClass(WatchboxCaller.class);
    verify(usecase).create(org.mockito.ArgumentMatchers.eq(request), caller.capture());
    assertThat(caller.getValue().fullName()).isNull();
    assertThat(caller.getValue().email()).isEqualTo("actor@example.test");
  }

  @Test
  void listReturnsVisibleWatchboxes() {
    var principal = TestPrincipals.oidcPrincipal(UUID.randomUUID());
    WatchboxResponse item = response(UUID.randomUUID());
    var caller = new WatchboxCaller(principal.internalUserId(), principal.isGlobalAdmin());
    when(usecase.list(caller)).thenReturn(List.of(item));

    var response = endpoint.list(principal);

    assertThat(response.getBody()).containsExactly(item);
  }

  @Test
  void patchReturnsOkAndDeleteReturnsNoContent() {
    var principal = TestPrincipals.oidcPrincipal(UUID.randomUUID());
    UUID watchboxId = UUID.randomUUID();
    var caller = new WatchboxCaller(principal.internalUserId(), principal.isGlobalAdmin());
    var request =
        new WatchboxPatchRequest(null, null, null, WatchboxDirection.ENTER, null, null, null, null);

    assertThat(endpoint.patch(watchboxId, request, principal).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(endpoint.delete(watchboxId, principal).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    verify(usecase).patch(watchboxId, request, caller);
    verify(usecase).delete(watchboxId, caller);
  }

  @Test
  void testFilterDelegatesValidatedRequestToNeutralPort() throws Exception {
    var principal = TestPrincipals.oidcPrincipal(UUID.randomUUID());
    var requestNode = new ObjectMapper().readTree("{\"filter\":{},\"feature\":{}}");
    var expected = new WatchboxFilterMatch(true, List.of());
    when(filterTesting.test(org.mockito.ArgumentMatchers.any())).thenReturn(expected);

    var response = endpoint.testFilter(requestNode, principal);

    assertThat(response.getBody()).isEqualTo(expected);
    var request =
        ArgumentCaptor.forClass(
            com.opencop.core.sdk.model.watchbox.WatchboxFilterTestRequest.class);
    verify(filterTesting).test(request.capture());
    assertThat(request.getValue().feature()).isEqualTo(requestNode.get("feature"));
  }

  private static WatchboxResponse response(UUID id) {
    return new WatchboxResponse(
        id,
        "watch",
        null,
        true,
        WatchboxDirection.BOTH,
        null,
        null,
        Set.of(),
        1,
        null,
        Set.of(),
        List.of(),
        new WatchboxCapabilities(true, true),
        null,
        null);
  }
}
