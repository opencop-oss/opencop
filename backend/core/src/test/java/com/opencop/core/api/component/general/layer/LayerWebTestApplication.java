// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.layer;

import com.opencop.core.api.component.support.CoreComponentWebTestConfig;
import com.opencop.core.api.general.business.model.connection.CurrentBrokerProvider;
import com.opencop.core.api.general.business.service.DataSourceRuntimeService;
import com.opencop.core.api.general.business.usecase.layer.ArchiveLayerUsecase;
import com.opencop.core.api.general.business.usecase.layer.CloneLayerUsecase;
import com.opencop.core.api.general.business.usecase.layer.CreateLayerUsecase;
import com.opencop.core.api.general.business.usecase.layer.LayerAuditProjection;
import com.opencop.core.api.general.business.usecase.layer.LayerMembersUsecase;
import com.opencop.core.api.general.business.usecase.layer.MergeLayerUsecase;
import com.opencop.core.api.general.business.usecase.layer.PatchLayerUsecase;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.cache.EventGeoCacheEvictionGateway;
import com.opencop.core.api.general.ports.gateway.job.JobPublisher;
import com.opencop.core.api.general.ports.gateway.kafka.KafkaProvisioningService;
import com.opencop.core.api.general.ports.technical.gateway.UserService;
import com.opencop.core.api.general.technical.endpoint.layer.LayersEndpoint;
import com.opencop.core.api.general.technical.gateway.db.connection.JdbcDataSourceRepository;
import com.opencop.core.api.general.technical.gateway.db.group.AppGroupLayerService;
import com.opencop.core.api.general.technical.gateway.db.layer.LayerMergeOperationEntity;
import com.opencop.core.api.general.technical.gateway.db.layer.LayerMergeOperationJpaRepository;
import com.opencop.core.api.general.technical.gateway.db.layer.PostgresLayerMergeOperationRepository;
import com.opencop.core.api.general.technical.gateway.db.layer.PostgresMergeLayerValidationGateway;
import com.opencop.core.api.general.technical.gateway.db.layergroup.AppLayerGroupLayerService;
import com.opencop.core.api.general.technical.service.LayerAuthorizationService;
import com.opencop.core.api.general.technical.service.LayerGroupAuthorizationService;
import com.opencop.core.api.iam.business.usecase.access.layer.LayerManagersFactory;
import com.opencop.core.api.iam.ports.gateway.repository.LayerMemberRepository;
import com.opencop.core.api.iam.ports.gateway.repository.LayersRepository;
import com.opencop.core.api.iam.ports.gateway.repository.ServerLayerRepository;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.api.iam.technical.gateway.db.layers.layer.LayersService;
import com.opencop.core.api.iam.technical.gateway.db.layers.member.LayerMembersService;
import com.opencop.core.api.iam.technical.gateway.db.server.layer.IamServerLayerRepository;
import com.opencop.core.api.iam.technical.gateway.db.server.layer.ServerLayerAccessService;
import com.opencop.core.api.iam.technical.gateway.db.server.layer.entity.ServerLayerAccessEntity;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.component.database.ComponentTestDatabaseConfig;
import com.opencop.core.sdk.model.job.JobSubmission;
import com.opencop.core.sdk.model.job.LayerCopyJobSubmission;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.broker.kafka.properties.LocalProviderProperties;
import com.opencop.core.sdk.technical.gateway.broker.kafka.properties.ProviderConfig;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupLayerMemberEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupMemberEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupLayerMemberRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.IamLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.LayerEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupUserGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupLayerEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupMemberEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupUserGroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.member.IamLayerMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.member.LayerMemberEntity;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.mockito.Mockito;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import tools.jackson.databind.ObjectMapper;

@SpringBootConfiguration
@EnableAutoConfiguration
@EnableMethodSecurity
@EntityScan(
    basePackageClasses = {
      LayerEntity.class,
      LayerMemberEntity.class,
      ServerLayerAccessEntity.class,
      GroupEntity.class,
      GroupMemberEntity.class,
      GroupLayerMemberEntity.class,
      LayerGroupEntity.class,
      LayerGroupMemberEntity.class,
      LayerGroupLayerEntity.class,
      LayerGroupUserGroupEntity.class,
      LayerMergeOperationEntity.class
    })
@EnableJpaRepositories(
    basePackageClasses = {
      IamLayerRepository.class,
      IamLayerMembersRepository.class,
      IamServerLayerRepository.class,
      IamGroupRepository.class,
      IamGroupMembersRepository.class,
      IamGroupLayerMemberRepository.class,
      IamLayerGroupMembersRepository.class,
      IamLayerGroupLayerRepository.class,
      IamLayerGroupUserGroupRepository.class,
      IamLayerGroupRepository.class,
      LayerMergeOperationJpaRepository.class
    })
@Import({
  ComponentTestDatabaseConfig.class,
  CoreComponentWebTestConfig.class,
  ArchiveLayerUsecase.class,
  CloneLayerUsecase.class,
  CreateLayerUsecase.class,
  LayerMembersUsecase.class,
  MergeLayerUsecase.class,
  PatchLayerUsecase.class,
  DataSourceRuntimeService.class,
  JdbcDataSourceRepository.class,
  LayersEndpoint.class,
  LayerAuthorizationService.class,
  AppGroupLayerService.class,
  AppLayerGroupLayerService.class,
  LayerGroupAuthorizationService.class,
  PostgresLayerMergeOperationRepository.class,
  PostgresMergeLayerValidationGateway.class
})
public class LayerWebTestApplication {

  @Bean
  CurrentBrokerProvider currentBrokerProvider(ProviderConfig providerConfig) {
    return new CurrentBrokerProvider(providerConfig.name());
  }

  // Mocked: these tests assert layer behaviour. Nothing here proves the rows land or roll back
  // with the change; only the mapper path has that coverage today.
  @Bean
  AuditRecorder auditRecorder() {
    return Mockito.mock(AuditRecorder.class);
  }

  @Bean
  LayerAuditProjection layerAuditProjection(ObjectMapper objectMapper) {
    return new LayerAuditProjection();
  }

  @Bean
  KafkaProvisioningService kafkaProvisioningService() {
    return Mockito.mock(KafkaProvisioningService.class);
  }

  @Bean
  JobPublisher jobPublisher(EntityManager entityManager) {
    return new RecordingLayerJobPublisher(entityManager);
  }

  @Bean
  EventGeoCacheEvictionGateway eventGeoCacheEvictionGateway() {
    return Mockito.mock(EventGeoCacheEvictionGateway.class);
  }

  @Bean
  LayersRepository layersRepository(IamLayerRepository repository, EntityManager entityManager) {
    return new LayersService(repository, _ -> Map.of(), entityManager);
  }

  @Bean
  LayerMemberRepository layerMemberRepository(
      IamLayerMembersRepository repository, IamLayerRepository layersRepository) {
    return new LayerMembersService(repository, layersRepository);
  }

  @Bean
  ServerLayerRepository serverLayerRepository(
      IamServerLayerRepository repository, IamLayerRepository layersRepository) {
    return new ServerLayerAccessService(repository, layersRepository);
  }

  @Bean
  LayerManager<Layer, LayerMember> layerManager(
      LayersRepository layersRepository,
      LayerMemberRepository layerMemberRepository,
      ServerLayerRepository serverLayerRepository,
      UserService userService,
      IamGroupMembersRepository groupMembersRepository,
      IamGroupLayerMemberRepository groupLayerRepository,
      IamLayerGroupMembersRepository layerGroupMembersRepository,
      IamLayerGroupLayerRepository layerGroupLayerRepository,
      IamLayerGroupUserGroupRepository layerGroupUserGroupRepository,
      IamLayerGroupRepository iamLayerGroupRepository) {
    return LayerManagersFactory.layerAccessManager(
        layersRepository,
        layerMemberRepository,
        serverLayerRepository,
        userService,
        groupMembersRepository,
        groupLayerRepository,
        layerGroupMembersRepository,
        layerGroupLayerRepository,
        layerGroupUserGroupRepository,
        iamLayerGroupRepository);
  }

  @Bean
  ProviderConfig providerConfig() {
    return new LocalProviderProperties("localhost:9092", "localhost:9092", 1_048_576L, 1);
  }

  static final class RecordingLayerJobPublisher implements JobPublisher {

    private final EntityManager entityManager;
    private final List<JobSubmission> submissions = new ArrayList<>();
    private final List<Consumer<Exception>> failureCallbacks = new ArrayList<>();

    private RecordingLayerJobPublisher(EntityManager entityManager) {
      this.entityManager = entityManager;
    }

    @Override
    public void publishNow(JobSubmission submission) {}

    @Override
    public void scheduleBatchAfterCommit(
        List<? extends JobSubmission> submissions, Runnable afterSuccessfulBatch) {
      scheduleBatchAfterCommit(submissions, afterSuccessfulBatch, _ -> {});
    }

    @Override
    public void scheduleBatchAfterCommit(
        List<? extends JobSubmission> submissions,
        Runnable afterSuccessfulBatch,
        Consumer<Exception> afterFailedPublish) {
      this.submissions.addAll(submissions);
      failureCallbacks.add(afterFailedPublish);
      if (submissions.size() != 1
          || !(submissions.get(0) instanceof LayerCopyJobSubmission layerCopyJob)) {
        return;
      }

      try {
        entityManager
            .createNativeQuery(
                "CALL geo.clone_source_to_layer(CAST(? AS uuid), CAST(? AS uuid), ?, ?)")
            .setParameter(1, layerCopyJob.sourceLayerId())
            .setParameter(2, layerCopyJob.targetLayerId())
            .setParameter(3, layerCopyJob.copyRemarks())
            .setParameter(4, layerCopyJob.copyMedia())
            .executeUpdate();
      } catch (RuntimeException e) {
        var message = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
        if (message != null
            && (message.contains("source id and target layer id must differ")
                || message.contains("target layer")
                || message.contains("has no features to clone")
                || message.contains("already has features"))) {
          throw new GlobalOpenCopException(message, HttpStatus.BAD_REQUEST);
        }
        throw new GlobalOpenCopException(
            message != null ? message : "Failed to clone layer contents",
            HttpStatus.INTERNAL_SERVER_ERROR);
      }
      afterSuccessfulBatch.run();
    }

    List<JobSubmission> submissions() {
      return List.copyOf(submissions);
    }

    void failLastPublish(Exception failure) {
      if (failureCallbacks.isEmpty()) {
        throw new IllegalStateException("No recorded publish failure callback");
      }
      failureCallbacks.getLast().accept(failure);
    }

    void clear() {
      submissions.clear();
      failureCallbacks.clear();
    }
  }
}
