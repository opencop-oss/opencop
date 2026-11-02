// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.extension;

import com.opencop.core.api.general.business.policy.connection.extension.ConnectionExtensionAccessPolicy;
import com.opencop.core.api.general.business.policy.connection.extension.ConnectionExtensionFailurePolicy;
import com.opencop.core.api.general.ports.gateway.authorization.ConnectionAuthorization;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.api.general.ports.usecase.connection.extension.ExtensionResult;
import com.opencop.core.api.general.ports.usecase.connection.extension.InvokeConnectionExtension;
import com.opencop.core.api.general.ports.usecase.connection.extension.InvokeConnectionExtensionCommand;
import com.opencop.core.api.general.ports.usecase.connection.extension.UnreadableExtensionBodyException;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExtensionBody;
import com.opencop.core.sdk.business.registry.integration.connection.extension.ConnectionExtensionRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.extension.OperationMatch;
import com.opencop.core.sdk.model.datasource.DataSource;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionExtensionContext;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionExtensionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.http.HttpStatus;

/**
 * Serves one request to a connection extension: resolves the connection, checks the caller may see
 * it, that its integration runs here, that the extension answers the request and that the caller
 * may invoke that operation, then hands the operation the connection's context.
 *
 * <p>The connection is resolved as {@link ExtensionConnections} describes.
 */
public class InvokeConnectionExtensionUsecase implements InvokeConnectionExtension {

  private static final Logger log = LoggerFactory.getLogger(InvokeConnectionExtensionUsecase.class);

  private final ExtensionConnections extensionConnections;
  private final ConnectionAuthorization authorization;
  private final ConnectionExtensionRegistry extensions;
  private final ExtensionContexts contexts;

  public InvokeConnectionExtensionUsecase(
      ExtensionConnections extensionConnections,
      ConnectionAuthorization authorization,
      ConnectionExtensionRegistry extensions,
      ExtensionContexts contexts) {
    this.extensionConnections = extensionConnections;
    this.authorization = authorization;
    this.extensions = extensions;
    this.contexts = contexts;
  }

  @Override
  public ExtensionResult invoke(InvokeConnectionExtensionCommand command) {
    var connection =
        extensionConnections.readable(
            command.sourceId(), command.userId(), command.isGlobalAdmin());
    var integration =
        extensionConnections
            .availableIntegration(connection)
            .orElseThrow(
                () ->
                    new ConnectionManagementException(
                        ConnectionExceptionCodes.CONNECTION_EXTENSION_NOT_FOUND,
                        command.extensionId()));
    var match = operation(integration, command);
    assertMayInvoke(match, command);
    try {
      var result = invokeOperation(connection, integration, match, command);
      logManaged(match, command, result.hasBody() ? HttpStatus.OK : HttpStatus.NO_CONTENT);
      return result;
    } catch (GlobalOpenCopException refused) {
      logManaged(match, command, refused.getHttpStatus());
      throw refused;
    } catch (RuntimeException unexpected) {
      logManaged(match, command, HttpStatus.INTERNAL_SERVER_ERROR);
      throw unexpected;
    }
  }

  private ExtensionResult invokeOperation(
      DataSource connection,
      IntegrationConnectionSchemaReference integration,
      OperationMatch match,
      InvokeConnectionExtensionCommand command) {
    var body = requestBody(match, command);
    var context =
        contexts.forInvocation(connection, integration, command.extensionId(), command.userId());
    return handle(match, context, command, body);
  }

  /**
   * Requires the caller to administer the connection when the operation changes something. A
   * refusal is traced like any other end of such an operation, then answered by the authorization's
   * own refusal.
   */
  private void assertMayInvoke(OperationMatch match, InvokeConnectionExtensionCommand command) {
    if (ConnectionExtensionAccessPolicy.requiresAdministration(match.operation().access())
        && !authorization.isDirectOwnerOrAdmin(
            command.sourceId(), command.userId(), command.isGlobalAdmin())) {
      logManaged(match, command, HttpStatus.FORBIDDEN);
      authorization.assertDirectOwnerOrAdmin(
          command.sourceId(), command.userId(), command.isGlobalAdmin());
    }
  }

  private OperationMatch operation(
      IntegrationConnectionSchemaReference integration, InvokeConnectionExtensionCommand command) {
    var extension =
        extensions
            .find(integration, command.extensionId())
            .orElseThrow(
                () ->
                    new ConnectionManagementException(
                        ConnectionExceptionCodes.CONNECTION_EXTENSION_NOT_FOUND,
                        command.extensionId()));
    return extension
        .match(command.method(), command.path())
        .orElseThrow(
            () ->
                new ConnectionManagementException(
                    ConnectionExceptionCodes.CONNECTION_EXTENSION_OPERATION_NOT_FOUND,
                    command.extensionId(),
                    command.method(),
                    "/" + String.join("/", command.path())));
  }

  private static Object requestBody(
      OperationMatch match, InvokeConnectionExtensionCommand command) {
    return switch (match.operation().request()) {
      case ExtensionBody.None _ -> null;
      case ExtensionBody.Json<?>(var type) -> {
        try {
          yield command.body().read(type);
        } catch (UnreadableExtensionBodyException _) {
          throw new ConnectionManagementException(ConnectionExceptionCodes.INVALID_REQUEST_BODY);
        }
      }
    };
  }

  private static ExtensionResult handle(
      OperationMatch match,
      ConnectionExtensionContext<Object> context,
      InvokeConnectionExtensionCommand command,
      Object body) {
    try {
      var value = match.handle(context, command.query(), body);
      return switch (match.operation().response()) {
        case ExtensionBody.None _ -> ExtensionResult.none();
        case ExtensionBody.Json<?>(var type) when type.isInstance(value) ->
            ExtensionResult.of(value);
        case ExtensionBody.Json<?> _ ->
            throw new IllegalStateException(
                "The handler returned no value of its declared response type");
      };
    } catch (ConnectionExtensionException failure) {
      var error = ConnectionExtensionFailurePolicy.toError(failure);
      logReported(match, context, command, failure, error.getCode());
      throw error;
    } catch (GlobalOpenCopException hostRefusal) {
      // Raised by the host's own gateways the extension called, such as a connection archived
      // during the invocation: already a deliberate answer, so it keeps its status.
      throw hostRefusal;
    } catch (RuntimeException unexpected) {
      log.error(
          "Connection extension operation failed: integration={} extension={} operation={} {}"
              + " sourceId={}",
          context.integration(),
          command.extensionId(),
          match.operation().method(),
          match.operation().path(),
          command.sourceId(),
          unexpected);
      throw new ConnectionManagementException(ConnectionExceptionCodes.EXTENSION_FAILED);
    }
  }

  /**
   * The caller sees only the extension's safe message, so the failure is logged with the cause the
   * extension attached: a remote system's failure for operators, a failure the request caused for
   * debugging only.
   */
  private static void logReported(
      OperationMatch match,
      ConnectionExtensionContext<Object> context,
      InvokeConnectionExtensionCommand command,
      ConnectionExtensionException failure,
      String code) {
    log.atLevel(ConnectionExtensionFailurePolicy.isUpstream(failure) ? Level.WARN : Level.DEBUG)
        .setCause(failure)
        .log(
            "Connection extension operation reported a failure: integration={} extension={}"
                + " operation={} {} sourceId={} code={}",
            context.integration(),
            command.extensionId(),
            match.operation().method(),
            match.operation().path(),
            command.sourceId(),
            code);
  }

  /**
   * Operations that change something leave a trace of who invoked them and how it ended, refusals
   * included.
   */
  private static void logManaged(
      OperationMatch match, InvokeConnectionExtensionCommand command, HttpStatus status) {
    if (ConnectionExtensionAccessPolicy.requiresAdministration(match.operation().access())) {
      log.info(
          "Connection extension operation invoked: userId={} sourceId={} extension={} operation={}"
              + " {} status={}",
          command.userId(),
          command.sourceId(),
          command.extensionId(),
          match.operation().method(),
          match.operation().path(),
          status.value());
    }
  }
}
