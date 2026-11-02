// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config.broker.azure;

import com.azure.resourcemanager.eventhubs.EventHubsManager;
import com.azure.resourcemanager.eventhubs.models.EntityStatus;
import com.opencop.core.api.general.ports.gateway.kafka.KafkaProvisioningService;
import com.opencop.core.sdk.ports.gateway.kafka.KafkaCredentials;
import com.opencop.core.sdk.ports.gateway.kafka.Password;
import com.opencop.core.sdk.ports.gateway.secrets.SecretStore;
import com.opencop.core.sdk.technical.gateway.broker.kafka.TopicSlug;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AzureEventHubsProvisioner implements KafkaProvisioningService {

  private static final Logger log = LoggerFactory.getLogger(AzureEventHubsProvisioner.class);
  private static final String SAS_RULE_TEMPLATE = "byod-%s-%s";

  private final EventHubsManager manager;
  private final String resourceGroup;
  private final String namespaceName;
  private final String bootstrapServer; // e.g., <ns>.servicebus.windows.net:9093
  private final SecretStore secretStore;
  private final int partitions;
  private final Duration dltPayloadRetention;

  public AzureEventHubsProvisioner(
      EventHubsManager manager,
      String resourceGroup,
      String namespaceName,
      String bootstrapServer,
      SecretStore secretStore,
      int partitions,
      Duration dltPayloadRetention) {
    this.manager = manager;
    this.resourceGroup = resourceGroup;
    this.namespaceName = namespaceName;
    this.bootstrapServer = bootstrapServer;
    this.secretStore = secretStore;
    this.partitions = partitions;
    this.dltPayloadRetention =
        dltPayloadRetention == null ? Duration.ofDays(7) : dltPayloadRetention;
  }

  @Override
  public KafkaCredentials provision(
      UUID sourceId, String sourceName, Long producerByteRateOverride) {
    var slug = TopicSlug.slugify(sourceName);
    var topicName = TOPIC_TEMPLATE.formatted(slug, sourceId);
    var sasRuleName = SAS_RULE_TEMPLATE.formatted(slug, sourceId);

    // Azure Event Hubs does not support per-producer quotas (Premium tier has namespace-level TUs).
    if (producerByteRateOverride != null) {
      log.warn(
          "Azure Event Hubs does not support per-producer quotas. Ignoring byte rate: {}",
          producerByteRateOverride);
    }

    try {
      manager
          .eventHubs()
          .define(topicName)
          .withExistingNamespace(resourceGroup, namespaceName)
          .withPartitionCount(partitions)
          .create();

      manager
          .eventHubs()
          .define(topicName + DLT_SUFFIX)
          .withExistingNamespace(resourceGroup, namespaceName)
          .withPartitionCount(1)
          .withRetentionPeriodInDays(dltRetentionDays())
          .create();

      // Create SAS rule with Send permission (= credential + WRITE ACL combined)
      var rule =
          manager
              .eventHubs()
              .authorizationRules()
              .define(sasRuleName)
              .withExistingEventHub(resourceGroup, namespaceName, topicName)
              .withSendAccess()
              .create();

      var connectionString = rule.getKeys().primaryConnectionString();

      // Store in Vault. Per Azure Event Hubs SASL/PLAIN protocol, the connection string is the
      // password.
      secretStore.patch(
          VAULT_PATH.formatted(sourceId),
          Map.of(
              "topicName", topicName,
              "username", "$ConnectionString",
              "password", connectionString,
              "bootstrapServer", bootstrapServer),
          null);

      log.info("Provisioned Azure Event Hub topic={} sasRule={}", topicName, sasRuleName);

      // Azure Kafka compatibility: connection string goes in the password field per
      // Azure Event Hubs docs (username is always "$ConnectionString").
      return new KafkaCredentials(
          topicName, "$ConnectionString", new Password(connectionString), bootstrapServer);
    } catch (Exception e) {
      log.error("Provisioning failed for sourceId={}, cleaning up: {}", sourceId, e.getMessage());
      deprovision(topicName, "$ConnectionString", sourceId);
      throw new RuntimeException("Failed to provision Azure Event Hub: " + topicName, e);
    }
  }

  @Override
  public void deprovision(String topicName, String username, UUID sourceId) {
    // Delete event hubs (cascades SAS rules)
    try {
      manager.eventHubs().deleteByName(resourceGroup, namespaceName, topicName);
    } catch (Exception e) {
      log.warn("Failed to delete event hub {}", topicName, e);
    }
    try {
      manager.eventHubs().deleteByName(resourceGroup, namespaceName, topicName + DLT_SUFFIX);
    } catch (Exception e) {
      log.warn("Failed to delete DLT event hub {}{}", topicName, DLT_SUFFIX, e);
    }
    try {
      secretStore.deleteAllVersions(VAULT_PATH.formatted(sourceId));
    } catch (Exception e) {
      log.warn("Failed to delete Vault credentials for {}", sourceId, e);
    }

    log.info("Deprovisioned Azure Event Hub topic={}", topicName);
  }

  @Override
  public Optional<Boolean> checkTopicExists(String topicName) {
    try {
      var eh = manager.eventHubs().getByName(resourceGroup, namespaceName, topicName);
      return Optional.of(eh != null);
    } catch (Exception e) {
      log.warn("Failed to check event hub existence for {}", topicName, e);
      return Optional.empty();
    }
  }

  @Override
  public Optional<Boolean> hasWriteAcl(String username, String topicName) {
    // On Azure, event hub enabled = write access (SAS rule stays, hub accepts/rejects)
    try {
      var eh = manager.eventHubs().getByName(resourceGroup, namespaceName, topicName);
      if (eh == null) return Optional.of(false);
      var status = eh.innerModel().status();
      // ACTIVE or null (default) means enabled
      return Optional.of(status == null || status == EntityStatus.ACTIVE);
    } catch (Exception e) {
      log.warn("Failed to check event hub status for {}", topicName, e);
      return Optional.empty();
    }
  }

  @Override
  public void removeWriteAcl(String username, String topicName) {
    // Disable event hub — producers get auth errors, SAS rule + key preserved
    setEventHubStatus(topicName, EntityStatus.DISABLED);
  }

  @Override
  public void addWriteAcl(String username, String topicName) {
    // Re-enable event hub — producer's existing connection string works again
    setEventHubStatus(topicName, EntityStatus.ACTIVE);
  }

  private void setEventHubStatus(String topicName, EntityStatus status) {
    try {
      // GET first — a partial PUT without partitionCount triggers Azure validation error
      var inner =
          manager.serviceClient().getEventHubs().get(resourceGroup, namespaceName, topicName);
      inner.withStatus(status);
      manager
          .serviceClient()
          .getEventHubs()
          .createOrUpdate(resourceGroup, namespaceName, topicName, inner);
      log.info(
          "Set event hub {} status to {} ({})",
          topicName,
          status,
          status == EntityStatus.DISABLED ? "stop" : "start");
    } catch (Exception e) {
      if (status == EntityStatus.ACTIVE) {
        throw new RuntimeException("Failed to enable event hub: " + topicName, e);
      }
      log.warn("Failed to disable event hub {}", topicName, e);
    }
  }

  private long dltRetentionDays() {
    var dayMillis = Duration.ofDays(1).toMillis();
    return Math.max(1L, (dltPayloadRetention.toMillis() + dayMillis - 1) / dayMillis);
  }
}
