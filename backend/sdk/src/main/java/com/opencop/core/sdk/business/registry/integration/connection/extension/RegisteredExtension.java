// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.registry.integration.connection.extension;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExtensionDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExtensionMethod;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExtensionPathTemplate;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionExtensionCapability;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionExtensionContext;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionExtensionException;
import com.opencop.core.sdk.ports.integration.connection.extension.ExtensionOperation;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** A registered connection extension, with its operations ready to dispatch to. */
public final class RegisteredExtension {

  private static final Comparator<ExtensionOperation<?, ?, ?>> MOST_SPECIFIC_FIRST =
      Comparator.comparing(ExtensionOperation::path, ExtensionPathTemplate.MOST_SPECIFIC_FIRST);

  private final ConnectionExtensionCapability<?> capability;
  private final IntegrationConnectionSchema schema;
  private final String id;
  private final ExtensionDescriptor descriptor;
  private final List<ExtensionOperation<?, ?, ?>> operations;
  private final List<ExtensionOperation<?, ?, ?>> dispatchOrder;

  RegisteredExtension(
      ConnectionExtensionCapability<?> capability,
      IntegrationConnectionSchema schema,
      String id,
      ExtensionDescriptor descriptor,
      List<ExtensionOperation<?, ?, ?>> operations) {
    this.capability = capability;
    this.schema = schema;
    this.id = id;
    this.descriptor = descriptor;
    this.operations = List.copyOf(operations);
    this.dispatchOrder = operations.stream().sorted(MOST_SPECIFIC_FIRST).toList();
  }

  /** The connection schema version whose connections carry the extension. */
  public IntegrationConnectionSchema schema() {
    return schema;
  }

  /** The identifier the extension is served under. */
  public String id() {
    return id;
  }

  public ExtensionDescriptor descriptor() {
    return descriptor;
  }

  /** The operations, in the order the extension declares them. */
  public List<ExtensionOperation<?, ?, ?>> operations() {
    return operations;
  }

  /**
   * Resolves the operation answering a request. When several match, the one with a literal where
   * the others have a variable, at the first segment they differ, answers.
   *
   * @param path the request path below the extension, as decoded segments
   */
  public Optional<OperationMatch> match(ExtensionMethod method, List<String> path) {
    for (var operation : dispatchOrder) {
      if (operation.method() == method) {
        var variables = operation.path().match(path);
        if (variables.isPresent()) {
          return Optional.of(new OperationMatch(operation, variables.get()));
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Hands the extension a deleted connection's context.
   *
   * <p>The registry has checked that the extension reads the settings type its schema resolves to,
   * so the context's settings are of the extension's own type.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public void afterConnectionDeleted(ConnectionExtensionContext<?> context)
      throws ConnectionExtensionException {
    ((ConnectionExtensionCapability) capability).afterConnectionDeleted(context);
  }
}
