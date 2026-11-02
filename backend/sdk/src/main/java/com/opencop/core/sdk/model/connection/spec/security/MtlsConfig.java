// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.connection.spec.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@JsonTypeName("mtls")
public record MtlsConfig(
    String[] enabledProtocols,
    String[] enabledCiphers,
    boolean hostnameVerification,
    KeystoreRef trustStore,
    KeystoreRef keyStore)
    implements TcpSecurity {

  /**
   * The client store first, then the truststore. A client-only connection keeps a truststore
   * reference without a URI, which references nothing.
   */
  @Override
  @JsonIgnore
  public List<String> secretRefs() {
    return Stream.of(keyStore, trustStore)
        .filter(Objects::nonNull)
        .map(KeystoreRef::uri)
        .filter(Objects::nonNull)
        .toList();
  }
}
