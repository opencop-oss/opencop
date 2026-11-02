// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config.broker.kafka;

import static com.opencop.core.sdk.technical.gateway.broker.kafka.KafkaNaming.DLT_SUFFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.ports.gateway.secrets.SecretStore;
import java.time.Duration;
import java.util.Collection;
import java.util.UUID;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AlterClientQuotasResult;
import org.apache.kafka.clients.admin.AlterUserScramCredentialsResult;
import org.apache.kafka.clients.admin.CreateAclsResult;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.config.TopicConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KafkaAdminClientProvisionerTest {

  @Test
  void provisionAppliesConfiguredRetentionOnlyToDltTopic() {
    var adminClient = mock(AdminClient.class);
    var secretStore = mock(SecretStore.class);
    var createTopicsResult = mock(CreateTopicsResult.class);
    var scramResult = mock(AlterUserScramCredentialsResult.class);
    var aclsResult = mock(CreateAclsResult.class);
    var quotasResult = mock(AlterClientQuotasResult.class);
    when(adminClient.createTopics(any(Collection.class))).thenReturn(createTopicsResult);
    when(createTopicsResult.all()).thenReturn(KafkaFuture.completedFuture(null));
    when(adminClient.alterUserScramCredentials(any())).thenReturn(scramResult);
    when(scramResult.all()).thenReturn(KafkaFuture.completedFuture(null));
    when(adminClient.createAcls(any())).thenReturn(aclsResult);
    when(aclsResult.all()).thenReturn(KafkaFuture.completedFuture(null));
    when(adminClient.alterClientQuotas(any())).thenReturn(quotasResult);
    when(quotasResult.all()).thenReturn(KafkaFuture.completedFuture(null));
    var retention = Duration.ofDays(3);
    var provisioner =
        new KafkaAdminClientProvisioner(
            adminClient, "localhost:9092", secretStore, 1024L, 4, retention);
    var sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440146");

    provisioner.provision(sourceId, "Test Source", null);

    @SuppressWarnings("unchecked")
    var topicsCaptor = ArgumentCaptor.forClass(Collection.class);
    org.mockito.Mockito.verify(adminClient, org.mockito.Mockito.times(2))
        .createTopics(topicsCaptor.capture());
    var topics =
        topicsCaptor.getAllValues().stream()
            .flatMap(topicsBatch -> ((Collection<NewTopic>) topicsBatch).stream())
            .toList();
    assertThat(topics)
        .filteredOn(topic -> topic.name().endsWith(DLT_SUFFIX))
        .singleElement()
        .satisfies(
            topic ->
                assertThat(topic.configs())
                    .containsEntry(
                        TopicConfig.RETENTION_MS_CONFIG, String.valueOf(retention.toMillis())));
    assertThat(topics)
        .filteredOn(topic -> !topic.name().endsWith(DLT_SUFFIX))
        .extracting(NewTopic::configs)
        .singleElement()
        .isNull();
  }
}
