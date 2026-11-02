// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config;

import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Installs the mapper profiles bundled with Processor from classpath JSON resources. */
@Component
public class BundledProfileInstaller {

  private static final Logger log = LoggerFactory.getLogger(BundledProfileInstaller.class);

  private static final String MAPPER_RESOURCES = "classpath:/profiles/mappers/*.json";

  private final MapperProfileService mappers;
  private final ResourcePatternResolver resources;
  private final ObjectMapper objectMapper;

  public BundledProfileInstaller(
      MapperProfileService mappers, ResourcePatternResolver resources, ObjectMapper objectMapper) {
    this.mappers = mappers;
    this.resources = resources;
    this.objectMapper = objectMapper;
  }

  void installMappers() {
    try {
      var found = resources.getResources(MAPPER_RESOURCES);
      if (found.length == 0) {
        log.info("No bundled mappers found under {}", MAPPER_RESOURCES);
      }
      for (var resource : found) {
        installMapper(resource);
      }
    } catch (IOException exception) {
      log.error("Failed to discover bundled mappers", exception);
    }
  }

  private void installMapper(Resource resource) {
    var filename = filename(resource);
    try {
      var mapper = read(resource);
      if (mapper.id() == null) {
        log.error("Bundled mapper does not contain a valid ID | file={}", filename);
        return;
      }
      if (mappers.mapperProfileExists(mapper.id())) {
        log.debug("Bundled mapper already exists | {} | file={}", mapper.name(), filename);
        return;
      }

      mappers.createMapperProfile(mapper, null, null, null);
      log.info(
          "Bundled mapper installed | {} | v{} | file={}",
          mapper.name(),
          mapper.version(),
          filename);
    } catch (DuplicateKeyException exception) {
      log.debug("Bundled mapper was installed concurrently | file={}", filename);
    } catch (Exception exception) {
      log.error("Failed to install bundled mapper | file={}", filename, exception);
    }
  }

  private MapperProfile read(Resource resource) throws IOException {
    try (InputStream input = resource.getInputStream()) {
      return objectMapper.readValue(input, MapperProfile.class);
    }
  }

  private static String filename(Resource resource) {
    var filename = resource.getFilename();
    return filename == null ? "<unknown>" : filename;
  }
}
