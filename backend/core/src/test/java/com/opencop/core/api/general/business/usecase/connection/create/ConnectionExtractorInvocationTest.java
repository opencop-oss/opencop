// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.create;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.policy.connection.DeclaredConnectionInputPolicy;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputValue;
import com.opencop.core.sdk.business.model.integration.connection.input.TcpConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.TextField;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.TcpConnectionMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.TcpEndpointMaterial;
import com.opencop.core.sdk.business.policy.integration.connection.material.MaterialValidationPolicy;
import com.opencop.core.sdk.integration.builtin.business.model.NativeConnectionSchema;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputException;
import com.opencop.core.sdk.ports.integration.connection.input.TcpConnectionExtractorCapability;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConnectionExtractorInvocationTest {

  private static final TcpConnectionInput INPUT = new TcpConnectionInput(List.of(), List.of());
  private static final ExtractorDescriptor DESCRIPTOR =
      new ExtractorDescriptor(
          UUID.fromString("5b0c7f1e-3f43-4f1a-9c55-2f1d7c0e8a11"),
          "Plain TCP",
          List.of(new TextField("host", "Host", true)));
  private static final TcpConnectionMaterial MATERIAL =
      TcpConnectionMaterial.plain(new TcpEndpointMaterial("feed.example.test", 8089));

  private final TcpConnectionExtractorCapability extractor =
      mock(TcpConnectionExtractorCapability.class);

  @SuppressWarnings("unchecked")
  private final MaterialValidationPolicy<TcpConnectionMaterial> resultPolicy =
      mock(MaterialValidationPolicy.class);

  @BeforeEach
  void declareTheExtractorsFields() {
    when(extractor.descriptor()).thenReturn(DESCRIPTOR);
  }

  @Test
  void answersWithTheMaterialTheHostAccepts() throws ConnectionInputException {
    when(extractor.extract(INPUT)).thenReturn(MATERIAL);

    assertThat(
            ConnectionExtractorInvocation.extract(
                NativeConnectionSchema.V1_REFERENCE, extractor, INPUT, resultPolicy))
        .isSameAs(MATERIAL);
  }

  /** The requester supplied valid input; material the host rejects is the plugin's failure. */
  @Test
  void answersMaterialTheHostRejectsAsAnInternalFailure() throws ConnectionInputException {
    when(extractor.extract(INPUT)).thenReturn(MATERIAL);
    doThrow(new IllegalArgumentException("port must be between 1 and 65535"))
        .when(resultPolicy)
        .validate(MATERIAL);

    assertThatThrownBy(
            () ->
                ConnectionExtractorInvocation.extract(
                    NativeConnectionSchema.V1_REFERENCE, extractor, INPUT, resultPolicy))
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            exception -> {
              assertThat(exception.getCode())
                  .isEqualTo(ConnectionExceptionCodes.EXTRACTOR_FAILED.code());
              assertThat(exception.getMessage()).doesNotContain("port");
            });
  }

  /** The requester supplied a field the extractor would skip, so it never runs. */
  @Test
  void rejectsAnUndeclaredFieldBeforeTheExtractorRuns() throws ConnectionInputException {
    var misspelt =
        new TcpConnectionInput(List.of(new ConnectionInputValue("hots", "a")), List.of());

    assertThatThrownBy(
            () ->
                ConnectionExtractorInvocation.extract(
                    NativeConnectionSchema.V1_REFERENCE, extractor, misspelt, resultPolicy))
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            exception -> {
              assertThat(exception.getCode())
                  .isEqualTo(ConnectionExceptionCodes.CONNECTION_INPUT_INVALID.code());
              assertThat(exception.getDetailCode())
                  .isEqualTo(DeclaredConnectionInputPolicy.UNDECLARED_FIELD);
              assertThat(exception.getField()).isEqualTo("hots");
            });
    verify(extractor, never()).validate(any());
    verify(extractor, never()).extract(any());
  }
}
