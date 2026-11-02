// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.profile.mapper;

import com.opencop.core.api.general.business.factory.profile.MapperProfileResponseFactory;
import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.profile.MapperProfileFullMetaResponse;
import com.opencop.core.api.general.business.model.profile.MapperProfileResponse;
import com.opencop.core.api.general.ports.usecase.profile.mapping.DeleteMappingProfile;
import com.opencop.core.api.general.ports.usecase.profile.mapping.ListMappingProfiles;
import com.opencop.core.api.general.ports.usecase.profile.mapping.SaveMappingProfile;
import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MapperProfileEndpoints {

  private static final String MAPPER_NOT_FOUND = "Mapper not found";

  // Unfiltered listings stay on ingress mappers, the only ones the mapper editor can show. A caller
  // asks for several directions by repeating the parameter or separating them with commas.
  private static final String QUALIFIER_PARAMETER = "qualifier";
  private static final String DEFAULT_QUALIFIER = "INGRESS";

  private final MapperProfileService service;
  private final SaveMappingProfile usecase;
  private final DeleteMappingProfile deleteUsecase;
  private final ListMappingProfiles listUsecase;
  private final MapperProfileResponseFactory profileResponseFactory;

  MapperProfileEndpoints(
      final MapperProfileService service,
      final SaveMappingProfile usecase,
      final DeleteMappingProfile deleteUsecase,
      final ListMappingProfiles listUsecase,
      final MapperProfileResponseFactory profileResponseFactory) {
    this.service = service;
    this.usecase = usecase;
    this.deleteUsecase = deleteUsecase;
    this.listUsecase = listUsecase;
    this.profileResponseFactory = profileResponseFactory;
  }

  @GetMapping("/v1/profile/mapping")
  public ResponseEntity<List<MapperProfileResponse>> getMappingProfiles(
      @RequestParam(name = QUALIFIER_PARAMETER, defaultValue = DEFAULT_QUALIFIER)
          final Set<MapperQualifier> qualifiers) {
    var profile =
        this.listUsecase.list(qualifiers).stream().map(profileResponseFactory::create).toList();
    return ResponseEntity.status(HttpStatus.OK).body(profile);
  }

  @GetMapping("/v1/profile/mapping/{mappingId}")
  public ResponseEntity<MapperProfileResponse> getMapperProfile(
      @PathVariable final UUID mappingId) {
    var stored = this.service.getLatestMapperProfileMeta(mappingId);
    if (stored == null) {
      throw new GlobalOpenCopException(MAPPER_NOT_FOUND, HttpStatus.NOT_FOUND);
    }
    var profile = profileResponseFactory.create(stored);
    return ResponseEntity.status(HttpStatus.OK).body(profile);
  }

  @PreAuthorize("hasRole('admin')")
  @GetMapping("/v1/admin/profile/mapping/{mappingId}")
  public ResponseEntity<MapperProfileFullMetaResponse> getMapperProfileFullMeta(
      @PathVariable final UUID mappingId) {
    var stored = this.service.getLatestMapperProfileFull(mappingId);
    if (stored == null) {
      throw new GlobalOpenCopException(MAPPER_NOT_FOUND, HttpStatus.NOT_FOUND);
    }
    var profile = profileResponseFactory.create(stored);
    return ResponseEntity.status(HttpStatus.OK).body(profile);
  }

  @PreAuthorize("hasRole('admin')")
  @GetMapping("/v1/admin/profile/mapping/{mappingId}/versions")
  public ResponseEntity<List<MapperProfileResponse>> getMapperProfileVersions(
      @PathVariable final UUID mappingId) {
    var stored = this.service.getAllMapperVersions(mappingId);
    if (stored.isEmpty()) {
      throw new GlobalOpenCopException(MAPPER_NOT_FOUND, HttpStatus.NOT_FOUND);
    }
    var versions = stored.stream().map(profileResponseFactory::create).toList();
    return ResponseEntity.status(HttpStatus.OK).body(versions);
  }

  @PreAuthorize("hasRole('admin')")
  @PostMapping("/v1/admin/profile/mapping")
  public ResponseEntity<Void> saveMapperProfile(
      @RequestBody MapperProfileFullMetaRequest request,
      @AuthenticationPrincipal OpenCopPrincipal authentication) {
    var id = this.usecase.create(request.toBusinessModel(), Caller.of(authentication));
    return ResponseEntity.created(URI.create("/v1/profile/mapping/" + id)).build();
  }

  @PreAuthorize("hasRole('admin')")
  @PatchMapping("/v1/admin/profile/mapping")
  public ResponseEntity<Void> updateMapperProfile(
      @RequestBody MapperProfileFullMetaRequest request,
      @AuthenticationPrincipal OpenCopPrincipal authentication) {
    this.usecase.update(request.toBusinessModel(), Caller.of(authentication));
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }

  @PreAuthorize("hasRole('admin')")
  @GetMapping("/v1/admin/profile/mapping")
  public ResponseEntity<List<MapperProfileResponse>> getMappingProfilesAdmin(
      @RequestParam(name = QUALIFIER_PARAMETER, defaultValue = DEFAULT_QUALIFIER)
          final Set<MapperQualifier> qualifiers) {
    var profiles =
        this.listUsecase.listIncludingArchived(qualifiers).stream()
            .map(profileResponseFactory::create)
            .toList();
    return ResponseEntity.status(HttpStatus.OK).body(profiles);
  }

  @PreAuthorize("hasRole('admin')")
  @DeleteMapping("/v1/admin/profile/mapping/{mappingId}")
  public ResponseEntity<Void> deleteMapperProfile(
      @PathVariable final UUID mappingId,
      @AuthenticationPrincipal OpenCopPrincipal authentication) {
    this.deleteUsecase.archive(mappingId, Caller.of(authentication));
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }
}
