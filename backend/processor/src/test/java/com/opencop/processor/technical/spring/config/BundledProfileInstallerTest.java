// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import tools.jackson.databind.ObjectMapper;

class BundledProfileInstallerTest {

  private static final UUID MAPPER_ID = UUID.fromString("7f6fd8a4-2444-4348-a8a6-8b5481863da6");

  @Test
  void installsABundledMapperThatIsNotStoredYet() throws Exception {
    var mappers = mock(MapperProfileService.class);
    var resources = mock(ResourcePatternResolver.class);
    var objectMapper = mock(ObjectMapper.class);
    var mapperResource = resource("mapper.json");
    var mapper =
        MapperProfile.builder()
            .withId(MAPPER_ID)
            .withName("JSON mapper")
            .withDescription("Data-authored")
            .withEditable(true)
            .withVersion(1)
            .withArchived(false)
            .build();
    when(resources.getResources("classpath:/profiles/mappers/*.json"))
        .thenReturn(new Resource[] {mapperResource});
    when(objectMapper.readValue(any(InputStream.class), eq(MapperProfile.class)))
        .thenReturn(mapper);
    when(mappers.mapperProfileExists(MAPPER_ID)).thenReturn(false);

    var installer = new BundledProfileInstaller(mappers, resources, objectMapper);
    installer.installMappers();

    verify(mappers).createMapperProfile(mapper, null, null, null);
  }

  private static Resource resource(String filename) throws Exception {
    var resource = mock(Resource.class);
    when(resource.getFilename()).thenReturn(filename);
    when(resource.getInputStream()).thenAnswer(_ -> new ByteArrayInputStream(new byte[0]));
    return resource;
  }
}
