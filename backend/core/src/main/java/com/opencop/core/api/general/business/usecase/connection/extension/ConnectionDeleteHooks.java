// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.extension;

import com.opencop.core.api.general.ports.gateway.connection.extension.DeleteHookRunner;
import com.opencop.core.api.general.ports.gateway.connection.extension.DeleteHookRunner.Outcome;
import com.opencop.core.api.general.ports.gateway.connection.extension.DeleteHookTelemetry;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.registry.integration.connection.extension.ConnectionExtensionRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.extension.RegisteredExtension;
import com.opencop.core.sdk.model.datasource.DataSource;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionExtensionContext;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lets a deleted connection's extensions release what they hold outside OpenCOP.
 *
 * <p>What they need is read before the deletion, which removes the connection's secret material;
 * the hooks run only once the deletion is final, one after another, each within a time limit. They
 * are best effort: nothing about them can prevent, delay beyond its limit, or fail a deletion. Once
 * they have run, each extension's own secret entry on the connection is deleted.
 */
public class ConnectionDeleteHooks {

  private static final Logger log = LoggerFactory.getLogger(ConnectionDeleteHooks.class);
  private static final Runnable NONE = () -> {};
  private static final String UNREADABLE =
      "Connection {} is deleted without its extensions' hooks: what they need cannot be read";

  private final ExtensionConnections connections;
  private final ConnectionExtensionRegistry extensions;
  private final ExtensionContexts contexts;
  private final ExtensionSecretEntries secretEntries;
  private final DeleteHookTelemetry telemetry;
  private final DeleteHookRunner runner;
  private final Duration timeout;

  public ConnectionDeleteHooks(
      ExtensionConnections connections,
      ConnectionExtensionRegistry extensions,
      ExtensionContexts contexts,
      ExtensionSecretEntries secretEntries,
      DeleteHookTelemetry telemetry,
      DeleteHookRunner runner,
      Duration timeout) {
    this.connections = connections;
    this.extensions = extensions;
    this.contexts = contexts;
    this.secretEntries = secretEntries;
    this.telemetry = telemetry;
    this.runner = runner;
    this.timeout = timeout;
  }

  /**
   * Reads what the connection's extensions will need, before the deletion removes it.
   *
   * @return what to run once the deletion is final: the hooks, then the deletion of the
   *     integration's secret entries, which runs without hooks too, as for an integration version
   *     declaring no extension, or not running here; nothing for a connection bound to no
   *     integration, or when what the hooks need cannot be read
   */
  public Runnable prepare(UUID sourceId, UUID userId) {
    try {
      var connection = connections.active(sourceId).orElse(null);
      if (connection == null || connection.integration() == null) {
        return NONE;
      }
      var integration = connection.integration();
      Runnable retireEntries = () -> secretEntries.retire(sourceId, integration.integrationId());
      if (!connections.runsHere(integration)) {
        log.info(
            "Connection {} is deleted without its extensions' hooks: integration {} v{} does not"
                + " run here",
            sourceId,
            integration.integrationId(),
            integration.schemaVersion());
        return retireEntries;
      }
      var declared = extensions.extensionsOf(integration);
      if (declared.isEmpty()) {
        return retireEntries;
      }
      var deleted = capture(sourceId, userId, connection, integration);
      return () -> {
        deleted.ifPresentOrElse(
            captured -> runInTurn(sourceId, declared, captured), () -> recordNotRun(declared));
        // The hooks have run by now, so the entries they may have read can go.
        retireEntries.run();
      };
    } catch (RuntimeException unreadable) {
      log.warn(UNREADABLE, sourceId, unreadable);
      return NONE;
    }
  }

  /**
   * What the hooks need, or nothing when it cannot be read: the connection is then deleted without
   * its hooks, though its extensions' secret entries are still deleted.
   */
  private Optional<ExtensionContexts.DeletedConnection> capture(
      UUID sourceId,
      UUID userId,
      DataSource connection,
      IntegrationConnectionSchemaReference integration) {
    try {
      return Optional.of(contexts.captureBeforeDeletion(connection, integration, userId));
    } catch (RuntimeException unreadable) {
      log.warn(UNREADABLE, sourceId, unreadable);
      return Optional.empty();
    }
  }

  /** Counted only once the deletion is final, so a deletion that failed leaves nothing behind. */
  private void recordNotRun(List<RegisteredExtension> extensions) {
    extensions.forEach(
        extension -> telemetry.recordNotRun(integrationOf(extension), extension.id()));
  }

  /**
   * Runs the hooks one after another. An interruption, such as a shutdown, stops the hooks not yet
   * run; nothing else stops the next one.
   */
  private void runInTurn(
      UUID sourceId,
      List<RegisteredExtension> declared,
      ExtensionContexts.DeletedConnection deleted) {
    for (int index = 0; index < declared.size(); index++) {
      var extension = declared.get(index);
      if (Thread.currentThread().isInterrupted()) {
        log.warn(
            "Delete hooks of connection {} stopped before extension {} of integration {}: interrupted",
            sourceId,
            extension.id(),
            integrationOf(extension));
        recordNotRun(declared.subList(index, declared.size()));
        return;
      }
      try {
        runBounded(sourceId, extension, deleted.contextFor(extension.id()));
      } catch (RuntimeException unexpected) {
        // The deletion is final by now: whatever goes wrong around a hook is only reported.
        log.warn(
            "Delete hook of extension {} of integration {} could not run for connection {}",
            extension.id(),
            integrationOf(extension),
            sourceId,
            unexpected);
        telemetry.recordFailure(integrationOf(extension), extension.id());
      }
    }
  }

  private void runBounded(
      UUID sourceId, RegisteredExtension extension, ConnectionExtensionContext<Object> context) {
    switch (runner.run(() -> extension.afterConnectionDeleted(context), timeout)) {
      case Outcome.Completed _ -> {
        // Released.
      }
      case Outcome.TimedOut _ -> {
        log.warn(
            "Delete hook of extension {} of integration {} did not finish within {} for connection"
                + " {}",
            extension.id(),
            integrationOf(extension),
            timeout,
            sourceId);
        telemetry.recordTimeout(integrationOf(extension), extension.id());
      }
      case Outcome.Failed(var cause) -> {
        log.warn(
            "Delete hook of extension {} of integration {} failed for connection {}",
            extension.id(),
            integrationOf(extension),
            sourceId,
            cause);
        telemetry.recordFailure(integrationOf(extension), extension.id());
      }
      case Outcome.Interrupted _ -> {
        log.warn(
            "Delete hook of extension {} of integration {} was interrupted for connection {}",
            extension.id(),
            integrationOf(extension),
            sourceId);
        telemetry.recordFailure(integrationOf(extension), extension.id());
      }
    }
  }

  private static UUID integrationOf(RegisteredExtension extension) {
    return extension.schema().reference().integrationId();
  }
}
