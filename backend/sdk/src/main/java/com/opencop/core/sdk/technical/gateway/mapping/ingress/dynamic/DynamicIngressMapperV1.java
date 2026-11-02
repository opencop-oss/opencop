// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.mapping.ingress.dynamic;

import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.model.mapper.MappingRequest;
import com.opencop.core.sdk.model.mapper.MappingResponse;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.DecodedMappingResult;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.PayloadIngressMapper;
import com.opencop.core.sdk.technical.mapper.MapperFacade;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import java.util.List;
import java.util.UUID;
import org.geojson.Feature;

public class DynamicIngressMapperV1 implements PayloadIngressMapper<Feature> {

  private final MapperProfile profile;
  private final MapperFacade facade;

  public DynamicIngressMapperV1(MapperProfile profile, MapperFacade facade) {
    this.profile = profile;
    this.facade = facade;
  }

  @Override
  public UUID id() {
    return profile.id();
  }

  @Override
  public String name() {
    return profile.name();
  }

  @Override
  public String description() {
    return profile.description();
  }

  @Override
  public int version() {
    return profile.version();
  }

  @Override
  public List<Feature> map(UUID id, MappingProperties properties, byte[] input) throws Exception {
    return mapResult(id, properties, input).features();
  }

  @Override
  public DecodedMappingResult mapResult(UUID id, MappingProperties properties, byte[] input) {
    MappingRequest request = new MappingRequest(properties, profile.dynamicMapper(), input);
    MappingResponse response = this.facade.map(request);
    return DecodedMappingResult.fromMappingResponse(
        profile.id(), profile.version(), profile.name(), response);
  }
}
