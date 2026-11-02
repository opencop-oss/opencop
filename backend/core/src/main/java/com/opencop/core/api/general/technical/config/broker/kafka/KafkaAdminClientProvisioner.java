// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config.broker.kafka;

import static com.opencop.core.sdk.technical.gateway.broker.kafka.KafkaNaming.DLT_SUFFIX;
import static com.opencop.core.sdk.technical.gateway.broker.kafka.KafkaNaming.TOPIC_TEMPLATE;

import com.opencop.core.api.general.ports.gateway.kafka.KafkaProvisioningService;
import com.opencop.core.sdk.ports.gateway.kafka.KafkaCredentials;
import com.opencop.core.sdk.ports.gateway.kafka.Password;
import com.opencop.core.sdk.ports.gateway.secrets.SecretStore;
import com.opencop.core.sdk.technical.gateway.broker.kafka.TopicSlug;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.ScramCredentialInfo;
import org.apache.kafka.clients.admin.ScramMechanism;
import org.apache.kafka.clients.admin.UserScramCredentialDeletion;
import org.apache.kafka.clients.admin.UserScramCredentialUpsertion;
import org.apache.kafka.common.acl.AccessControlEntry;
import org.apache.kafka.common.acl.AccessControlEntryFilter;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.acl.AclPermissionType;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.apache.kafka.common.quota.ClientQuotaAlteration;
import org.apache.kafka.common.quota.ClientQuotaEntity;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourcePattern;
import org.apache.kafka.common.resource.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Provisions Kafka resources (topic, SCRAM user, ACLs, quota) for each BYOD data source.
// Topics, SCRAM credentials, and ACLs are stored in KRaft cluster metadata and persist across
// broker restarts.
public class KafkaAdminClientProvisioner implements KafkaProvisioningService {

  private static final Logger log = LoggerFactory.getLogger(KafkaAdminClientProvisioner.class);
  private static final String USER_TEMPLATE = "byod-%s-%s";
  private static final short DEFAULT_REPLICATION = 1;
  private static final int SCRAM_ITERATIONS = 4096;

  private final AdminClient adminClient;
  // saslBootstrap currently uses SASL_PLAINTEXT, so cross-network producer traffic is unencrypted.
  // Use SASL_SSL before exposing it across networks; SCRAM credentials work with either listener.
  private final String saslBootstrap;
  private final SecretStore secretStore;
  private final long defaultProducerByteRate;
  private final int topicPartitions;
  private final Duration dltPayloadRetention;

  public KafkaAdminClientProvisioner(
      AdminClient adminClient,
      String saslBootstrap,
      SecretStore secretStore,
      long defaultProducerByteRate,
      int topicPartitions,
      Duration dltPayloadRetention) {
    this.adminClient = adminClient;
    this.saslBootstrap = saslBootstrap;
    this.secretStore = secretStore;
    this.defaultProducerByteRate = defaultProducerByteRate;
    this.topicPartitions = topicPartitions;
    this.dltPayloadRetention =
        dltPayloadRetention == null ? Duration.ofDays(7) : dltPayloadRetention;
  }

  @Override
  public KafkaCredentials provision(
      UUID sourceId, String sourceName, Long producerByteRateOverride) {
    var slug = TopicSlug.slugify(sourceName);
    // Topic format: opencop.byod.<slug>.<uuid>
    // Contract: the UUID is always the last '.'-delimited segment. Processor's ByodIngestConsumer
    // relies on this to extract the sourceId. If the naming scheme changes, preserve this suffix.
    var topicName = TOPIC_TEMPLATE.formatted(slug, sourceId);
    var username = USER_TEMPLATE.formatted(slug, sourceId);
    // URL-safe base64 (charset [A-Za-z0-9_-]) — safe for shell, env vars, YAML, JSON, K8s secrets.
    var passwordBytes = new byte[32];
    new SecureRandom().nextBytes(passwordBytes);
    var password =
        new Password(Base64.getUrlEncoder().withoutPadding().encodeToString(passwordBytes));
    var producerByteRate =
        producerByteRateOverride != null ? producerByteRateOverride : defaultProducerByteRate;

    try {
      createTopic(topicName);
      createDltTopic(topicName + DLT_SUFFIX);
      createScramUser(username, password);
      createAcls(username, topicName);
      setQuota(username, producerByteRate);
      storeCredentials(sourceId, topicName, username, password);
    } catch (Exception e) {
      log.error("Provisioning failed for sourceId={}, cleaning up: {}", sourceId, e.getMessage());
      deprovision(topicName, username, sourceId);
      throw e;
    }

    log.info("Provisioned BYOD topic={} user={}", topicName, username);
    return new KafkaCredentials(topicName, username, password, saslBootstrap);
  }

  @Override
  public void deprovision(String topicName, String username, UUID sourceId) {
    // Reverse order of creation, best-effort each step.
    // Vault credentials deleted last — if SCRAM user delete fails, we still have a
    // reference to the orphaned credential. Vault can always be re-deleted.
    deleteQuota(username);
    deleteAcls(username, topicName);
    deleteScramUser(username);
    deleteTopic(topicName + DLT_SUFFIX);
    deleteTopic(topicName);
    deleteCredentials(sourceId);

    log.info("Deprovisioned BYOD topic={} user={}", topicName, username);
  }

  private void createTopic(String topicName) {
    createTopic(topicName, Map.of());
  }

  private void createDltTopic(String topicName) {
    createTopic(
        topicName,
        Map.of(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(dltPayloadRetention.toMillis())));
  }

  private void createTopic(String topicName, Map<String, String> topicConfigs) {
    try {
      // No explicit max.message.bytes — relies on broker default (1MB), consistent with
      // Connect's maxMessageSizeBytes for TCP. Kafka enforces at the broker; producers get
      // non-retriable RecordTooLargeException. To set per-topic limits, add
      // topic.configs(Map.of("max.message.bytes", ...)) here.
      var topic = new NewTopic(topicName, topicPartitions, DEFAULT_REPLICATION);
      if (!topicConfigs.isEmpty()) {
        topic.configs(topicConfigs);
      }
      adminClient.createTopics(Set.of(topic)).all().get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Failed to create topic: " + topicName, e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof TopicExistsException) {
        log.info("Topic already exists (idempotent): {}", topicName);
        return;
      }
      throw new RuntimeException("Failed to create topic: " + topicName, e);
    }
  }

  private void createScramUser(String username, Password password) {
    try {
      var upsert =
          new UserScramCredentialUpsertion(
              username,
              new ScramCredentialInfo(ScramMechanism.SCRAM_SHA_256, SCRAM_ITERATIONS),
              password.value().getBytes(StandardCharsets.UTF_8));
      adminClient.alterUserScramCredentials(List.of(upsert)).all().get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Failed to create SCRAM user: " + username, e);
    } catch (ExecutionException e) {
      throw new RuntimeException("Failed to create SCRAM user: " + username, e);
    }
  }

  private void createAcls(String username, String topicName) {
    try {
      var resource = new ResourcePattern(ResourceType.TOPIC, topicName, PatternType.LITERAL);
      var writeEntry =
          new AccessControlEntry(
              "User:" + username, "*", AclOperation.WRITE, AclPermissionType.ALLOW);
      var describeEntry =
          new AccessControlEntry(
              "User:" + username, "*", AclOperation.DESCRIBE, AclPermissionType.ALLOW);

      adminClient
          .createAcls(
              List.of(
                  new AclBinding(resource, writeEntry), new AclBinding(resource, describeEntry)))
          .all()
          .get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Failed to create ACLs for: " + topicName, e);
    } catch (ExecutionException e) {
      throw new RuntimeException("Failed to create ACLs for: " + topicName, e);
    }
  }

  private void deleteTopic(String topicName) {
    try {
      adminClient.deleteTopics(Set.of(topicName)).all().get();
    } catch (Exception e) {
      log.warn("Failed to delete topic {}: {}", topicName, e.getMessage());
    }
  }

  private void deleteScramUser(String username) {
    try {
      var deletion = new UserScramCredentialDeletion(username, ScramMechanism.SCRAM_SHA_256);
      adminClient.alterUserScramCredentials(List.of(deletion)).all().get();
    } catch (Exception e) {
      log.warn("Failed to delete SCRAM user {}: {}", username, e.getMessage());
    }
  }

  private void deleteAcls(String username, String topicName) {
    try {
      var resource = new ResourcePattern(ResourceType.TOPIC, topicName, PatternType.LITERAL);
      var filter =
          new AclBindingFilter(
              resource.toFilter(),
              new AccessControlEntryFilter(
                  "User:" + username, null, AclOperation.ANY, AclPermissionType.ANY));
      adminClient.deleteAcls(List.of(filter)).all().get();
    } catch (Exception e) {
      log.warn("Failed to delete ACLs for {}: {}", topicName, e.getMessage());
    }
  }

  private void setQuota(String username, long producerByteRate) {
    try {
      var entity = new ClientQuotaEntity(Map.of(ClientQuotaEntity.USER, username));
      var alteration =
          new ClientQuotaAlteration(
              entity,
              List.of(
                  new ClientQuotaAlteration.Op("producer_byte_rate", (double) producerByteRate)));
      adminClient.alterClientQuotas(List.of(alteration)).all().get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Failed to set quota for user: " + username, e);
    } catch (ExecutionException e) {
      throw new RuntimeException("Failed to set quota for user: " + username, e);
    }
  }

  private void deleteQuota(String username) {
    try {
      var entity = new ClientQuotaEntity(Map.of(ClientQuotaEntity.USER, username));
      // null value removes the quota entry
      var alteration =
          new ClientQuotaAlteration(
              entity, List.of(new ClientQuotaAlteration.Op("producer_byte_rate", null)));
      adminClient.alterClientQuotas(List.of(alteration)).all().get();
    } catch (Exception e) {
      log.warn("Failed to delete quota for user {}: {}", username, e.getMessage());
    }
  }

  private void storeCredentials(
      UUID sourceId, String topicName, String username, Password password) {
    var ref = VAULT_PATH.formatted(sourceId);
    secretStore.patch(
        ref,
        Map.of(
            "topicName", topicName,
            "username", username,
            "password", password.value(),
            "bootstrapServer", saslBootstrap),
        null);
  }

  private void deleteCredentials(UUID sourceId) {
    try {
      secretStore.deleteAllVersions(VAULT_PATH.formatted(sourceId));
    } catch (Exception e) {
      log.warn("Failed to delete Vault credentials for {}: {}", sourceId, e.getMessage());
    }
  }

  @Override
  public Optional<Boolean> hasWriteAcl(String username, String topicName) {
    try {
      var resource = new ResourcePattern(ResourceType.TOPIC, topicName, PatternType.LITERAL);
      var filter =
          new AclBindingFilter(
              resource.toFilter(),
              new AccessControlEntryFilter(
                  "User:" + username, null, AclOperation.WRITE, AclPermissionType.ALLOW));
      var results = adminClient.describeAcls(filter).values().get();
      return Optional.of(!results.isEmpty());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Interrupted checking WRITE ACL for {} on {}", username, topicName);
      return Optional.empty();
    } catch (ExecutionException e) {
      log.warn("Failed to check WRITE ACL for {} on {}: {}", username, topicName, e.getMessage());
      return Optional.empty();
    }
  }

  @Override
  public void removeWriteAcl(String username, String topicName) {
    try {
      var resource = new ResourcePattern(ResourceType.TOPIC, topicName, PatternType.LITERAL);
      var filter =
          new AclBindingFilter(
              resource.toFilter(),
              new AccessControlEntryFilter(
                  "User:" + username, null, AclOperation.WRITE, AclPermissionType.ALLOW));
      adminClient.deleteAcls(List.of(filter)).all().get();
      log.info("Removed WRITE ACL for user={} on topic={}", username, topicName);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Failed to remove WRITE ACL for: " + topicName, e);
    } catch (ExecutionException e) {
      throw new RuntimeException("Failed to remove WRITE ACL for: " + topicName, e);
    }
  }

  @Override
  public void addWriteAcl(String username, String topicName) {
    try {
      var resource = new ResourcePattern(ResourceType.TOPIC, topicName, PatternType.LITERAL);
      var writeEntry =
          new AccessControlEntry(
              "User:" + username, "*", AclOperation.WRITE, AclPermissionType.ALLOW);
      adminClient.createAcls(List.of(new AclBinding(resource, writeEntry))).all().get();
      log.info("Added WRITE ACL for user={} on topic={}", username, topicName);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Failed to add WRITE ACL for: " + topicName, e);
    } catch (ExecutionException e) {
      throw new RuntimeException("Failed to add WRITE ACL for: " + topicName, e);
    }
  }

  @Override
  public Optional<Boolean> checkTopicExists(String topicName) {
    try {
      adminClient.describeTopics(Set.of(topicName)).topicNameValues().get(topicName).get();
      return Optional.of(true);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof UnknownTopicOrPartitionException) {
        return Optional.of(false);
      }
      log.warn("Unexpected error checking topic existence for {}: {}", topicName, e.getMessage());
      return Optional.empty();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Interrupted checking topic existence for {}", topicName);
      return Optional.empty();
    } catch (Exception e) {
      log.warn("Failed to check topic existence for {}: {}", topicName, e.getMessage());
      return Optional.empty();
    }
  }
}
