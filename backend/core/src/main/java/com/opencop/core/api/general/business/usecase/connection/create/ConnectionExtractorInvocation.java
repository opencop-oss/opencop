// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.create;

import com.opencop.core.api.general.business.policy.connection.DeclaredConnectionInputPolicy;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInput;
import com.opencop.core.sdk.business.policy.integration.connection.material.MaterialValidationPolicy;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionExtractorCapability;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputException;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a selected extractor over a request's input, translating what the extractor reports into the
 * API's answer.
 *
 * <p>The extractor sees the input only once every value and file in it names a field its descriptor
 * declares, so a misspelt or misplaced field is reported instead of skipped.
 *
 * <p>Invalid input is the requester's to fix, so its safe explanation, detail code, and field reach
 * the client. Any other failure is the plugin's or the host's, so the client learns only that
 * something went wrong, and the log records which extractor failed and why.
 */
public final class ConnectionExtractorInvocation {

  private static final Logger log = LoggerFactory.getLogger(ConnectionExtractorInvocation.class);

  private ConnectionExtractorInvocation() {}

  /** The connection material the extractor produces from the input, once it accepts the input. */
  public static <I extends ConnectionInput, M> M extract(
      IntegrationConnectionSchemaReference integration,
      ConnectionExtractorCapability<I, M> extractor,
      I input) {
    return extract(integration, extractor, input, _ -> {});
  }

  /**
   * The connection material the extractor produces from the input, once it accepts the input and
   * the host accepts what it produced. Material the host rejects is the plugin's failure, never the
   * requester's.
   */
  public static <I extends ConnectionInput, M> M extract(
      IntegrationConnectionSchemaReference integration,
      ConnectionExtractorCapability<I, M> extractor,
      I input,
      MaterialValidationPolicy<M> resultPolicy) {
    DeclaredConnectionInputPolicy.requireDeclared(extractor.descriptor(), input);
    try {
      extractor.validate(input);
      var material = extractor.extract(input);
      resultPolicy.validate(material);
      return material;
    } catch (ConnectionInputException exception) {
      if (exception.failure() == ConnectionInputFailure.INVALID_INPUT) {
        throw ConnectionManagementException.inputRejected(exception);
      }
      log.error(
          "Connection extractor failed: integration={} extractor={} failure={} detail={}",
          integration,
          extractor.id(),
          exception.failure(),
          exception.providerDetailCode().orElse("none"),
          exception);
      throw internalFailure();
    } catch (RuntimeException exception) {
      log.error(
          "Connection extractor failed: integration={} extractor={}",
          integration,
          extractor.id(),
          exception);
      throw internalFailure();
    }
  }

  private static ConnectionManagementException internalFailure() {
    return new ConnectionManagementException(ConnectionExceptionCodes.EXTRACTOR_FAILED);
  }
}
