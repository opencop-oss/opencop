// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.connection.extension;

import com.opencop.core.api.general.ports.usecase.connection.extension.ConnectionExtensionSummary;
import com.opencop.core.api.general.ports.usecase.connection.extension.ListConnectionExtensions;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ListConnectionExtensionsEndpoint {

  private final ListConnectionExtensions usecase;

  ListConnectionExtensionsEndpoint(ListConnectionExtensions usecase) {
    this.usecase = usecase;
  }

  @Operation(
      summary = "List a connection's extensions",
      description =
          """
          Returns the extensions the connection's integration adds to it, each with only the \
          operations the caller may invoke: those that read for anyone who can see the \
          connection, and every one for its direct owner or a global admin. An extension the \
          caller can invoke nothing of is left out. A connection the caller cannot see answers \
          404, and one whose integration this runtime does not run answers 409 \
          INTEGRATION_UNAVAILABLE.""")
  @GetMapping("/v1/connections/{sourceId}/extensions")
  public List<ConnectionExtensionSummary> list(
      @AuthenticationPrincipal OpenCopPrincipal principal, @PathVariable UUID sourceId) {
    return usecase.list(sourceId, principal.internalUserId(), principal.isGlobalAdmin());
  }
}
