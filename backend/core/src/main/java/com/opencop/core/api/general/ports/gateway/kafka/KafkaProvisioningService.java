// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.kafka;

import com.opencop.core.sdk.ports.gateway.kafka.KafkaCredentials;
import com.opencop.core.sdk.technical.gateway.broker.kafka.KafkaNaming;
import java.util.Optional;
import java.util.UUID;

public interface KafkaProvisioningService {

  /**
   * @see KafkaNaming#TOPIC_TEMPLATE
   */
  String TOPIC_TEMPLATE = KafkaNaming.TOPIC_TEMPLATE;

  /**
   * Logical Vault KV v2 path for BYOD Kafka data source credentials. Reads and writes use the
   * corresponding {@code data} namespace; permanent deletion uses {@code metadata}.
   */
  String VAULT_PATH = "opencop/shared/kafka-ds/%s";

  /**
   * @see KafkaNaming#DLT_SUFFIX
   */
  String DLT_SUFFIX = KafkaNaming.DLT_SUFFIX;

  /**
   * Provisions a new BYOD data source: creates the topic/event hub, sets up producer credentials
   * and write access, and stores credentials in Vault.
   *
   * <p>Provider mapping:
   *
   * <ul>
   *   <li>Kafka: creates topic, SCRAM user, ACL, quota
   *   <li>Azure Event Hubs: creates event hub, SAS authorization rule with Send access
   * </ul>
   *
   * @param sourceName user-provided data source name (used for human-readable topic slug)
   */
  KafkaCredentials provision(UUID sourceId, String sourceName, Long producerByteRateOverride);

  /**
   * Deprovisions a BYOD data source: removes the topic/event hub and associated credentials.
   * Best-effort (logs failures, does not throw).
   *
   * <p>Provider mapping:
   *
   * <ul>
   *   <li>Kafka: deletes topic, SCRAM user, ACL, quota
   *   <li>Azure Event Hubs: deletes event hub (cascades SAS rules)
   * </ul>
   *
   * @param topicName the provisioned topic name (from connection_spec in DB)
   * @param username the provisioned SCRAM username (from connection_spec in DB)
   * @param sourceId used for Vault credential path
   */
  void deprovision(String topicName, String username, UUID sourceId);

  /** Checks whether the topic/event hub exists. Returns empty if the broker is unreachable. */
  Optional<Boolean> checkTopicExists(String topicName);

  /**
   * Checks whether the producer has write access to the topic/event hub. Returns empty if the
   * broker is unreachable.
   *
   * <p>Provider mapping:
   *
   * <ul>
   *   <li>Kafka: checks WRITE ACL for the user
   *   <li>Azure Event Hubs: checks event hub status (ACTIVE = write allowed)
   * </ul>
   */
  Optional<Boolean> hasWriteAcl(String username, String topicName);

  /**
   * Disables write access. Best-effort — logs failures, does not throw. Used by stop to prevent
   * external producers from writing.
   *
   * <p>Provider mapping:
   *
   * <ul>
   *   <li>Kafka: removes the WRITE ACL (keeps DESCRIBE)
   *   <li>Azure Event Hubs: sets event hub status to DISABLED
   * </ul>
   */
  void removeWriteAcl(String username, String topicName);

  /**
   * Enables write access. Throws on failure — caller needs to know if start didn't take effect.
   *
   * <p>Provider mapping:
   *
   * <ul>
   *   <li>Kafka: re-adds the WRITE ACL (idempotent)
   *   <li>Azure Event Hubs: sets event hub status to ACTIVE
   * </ul>
   */
  void addWriteAcl(String username, String topicName);
}
