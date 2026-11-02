// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.push;

import com.opencop.core.api.general.business.policy.ResourceLifecyclePolicy;
import com.opencop.core.api.general.business.policy.connection.access.AllowedAccessModesPolicy;
import com.opencop.core.api.general.business.policy.connection.access.EgressMapperAvailabilityPolicy;
import com.opencop.core.api.general.business.service.ConnectRoutingTargetResolver;
import com.opencop.core.api.general.business.service.DataSourceRuntimeService;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.api.general.ports.usecase.connection.push.PushFeatures;
import com.opencop.core.sdk.business.model.integration.mapping.egress.EgressMapperContributions;
import com.opencop.core.sdk.model.datasource.DataSourceRuntime;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.api.ConnectService;
import com.opencop.core.sdk.ports.gateway.api.ConnectServiceException;
import com.opencop.core.sdk.ports.gateway.iam.AccessRepository;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.ports.gateway.mapping.egress.PayloadEgressMapper;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.MappingException;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PushFeaturesUsecase implements PushFeatures {
  private static final String PUSH_OPERATION = "push features";

  private final ConnectService connectService;
  private final DataSourceRuntimeService runtimeService;
  private final ConnectRoutingTargetResolver routingTargetResolver;
  private final AccessRepository<ServerRole> sourceAccess;
  private final EgressMapperContributions egressMappers;
  private final MapperProfileService mappers;

  public PushFeaturesUsecase(
      EgressMapperContributions egressMappers,
      MapperProfileService mappers,
      ConnectService connectService,
      DataSourceRuntimeService runtimeService,
      ConnectRoutingTargetResolver routingTargetResolver,
      AccessRepository<ServerRole> sourceAccess) {
    this.egressMappers = egressMappers;
    this.mappers = mappers;
    this.connectService = connectService;
    this.runtimeService = runtimeService;
    this.routingTargetResolver = routingTargetResolver;
    this.sourceAccess = sourceAccess;
  }

  @Override
  public void execute(UUID sourceId, UUID userId, FeatureCollection features) {
    var role = sourceAccess.userRole(userId, sourceId);
    if (role == null || role.isLowerThan(ServerRole.ADMIN))
      throw new GlobalOpenCopException("Insufficient permissions", HttpStatus.FORBIDDEN);

    // Check if pushing enabled.
    final DataSourceRuntime dataSourceRuntime =
        runtimeService.getWithRuntime(sourceId, RecordVisibility.ALL);
    ResourceLifecyclePolicy.assertDataSourceActive(dataSourceRuntime.dataSource(), PUSH_OPERATION);
    if (!dataSourceRuntime.dataSource().access().canWrite())
      throw new GlobalOpenCopException(
          "Source " + sourceId + " cannot be written to.", HttpStatus.FORBIDDEN);

    if (!(dataSourceRuntime.dataSource() instanceof SocketDataSource socket)) {
      throw new GlobalOpenCopException(
          "No routable Connect instance available for source " + sourceId,
          HttpStatus.SERVICE_UNAVAILABLE);
    }
    assertTransportAllows(socket);
    var egressMapper = egressMapper(socket);

    var target =
        routingTargetResolver
            .resolveKnownOwnerTarget(sourceId, socket.assignedInstance(), "PUSH")
            .orElseThrow(
                () ->
                    new GlobalOpenCopException(
                        "No routable Connect instance available for source " + sourceId,
                        HttpStatus.SERVICE_UNAVAILABLE));

    final var bytes = encode(egressMapper, features.getFeatures());
    try {
      connectService.pushOnInstance(sourceId, bytes, target);
    } catch (ConnectServiceException _) {
      throw new GlobalOpenCopException(
          "Failed to push on source.", HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /** A stored connection may predate its transport's access rule: its transport decides again. */
  private static void assertTransportAllows(SocketDataSource socket) {
    AllowedAccessModesPolicy.assertTransportAllows(socket.provider(), socket.access());
  }

  /** The egress mapper the connection writes with, once it is known to be usable. */
  private PayloadEgressMapper egressMapper(SocketDataSource socket) {
    var egressMapperId = socket.egressMapperId();
    if (egressMapperId == null) {
      throw new ConnectionManagementException(
          ConnectionExceptionCodes.EGRESS_MAPPER_REQUIRED, socket.access());
    }
    return EgressMapperAvailabilityPolicy.requireAvailable(
        egressMappers.find(egressMapperId), mappers.findLatest(egressMapperId));
  }

  /**
   * Encodes the complete batch before any payload reaches the transport, so a failed feature sends
   * nothing. A feature the egress mapper declines is rejected with the mapper's own code.
   */
  private static List<byte[]> encode(PayloadEgressMapper egressMapper, List<Feature> features) {
    var encoded = new ArrayList<byte[]>(features.size());
    for (var feature : features) {
      try {
        encoded.add(egressMapper.map(feature));
      } catch (MappingException declined) {
        throw new GlobalOpenCopException(
            declined.code().name(), declined.getMessage(), HttpStatus.BAD_REQUEST);
      } catch (RuntimeException exception) {
        throw exception;
      } catch (Exception exception) {
        throw new IllegalStateException("Egress mapper failed to encode a feature", exception);
      }
    }
    return List.copyOf(encoded);
  }
}
