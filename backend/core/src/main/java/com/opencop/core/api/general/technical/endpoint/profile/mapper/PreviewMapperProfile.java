// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.profile.mapper;

import com.opencop.core.sdk.model.mapper.MappingRequest;
import com.opencop.core.sdk.model.mapper.MappingResponse;
import com.opencop.core.sdk.technical.mapper.MapperFacade;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/profile/mapping/preview")
public class PreviewMapperProfile {

  private final MapperFacade facade;

  PreviewMapperProfile(MapperFacade facade) {
    this.facade = facade;
  }

  //  @PreAuthorize("hasRole('admin')")
  @PostMapping
  public ResponseEntity<MappingResponse> preview(@RequestBody PreviewMappingRequest request) {
    var mappingRequest =
        new MappingRequest(
            new MappingProperties(null, UUID.randomUUID(), true),
            request.profile(),
            request.payloadBytes());
    return ResponseEntity.ok(facade.map(mappingRequest));
  }
}
