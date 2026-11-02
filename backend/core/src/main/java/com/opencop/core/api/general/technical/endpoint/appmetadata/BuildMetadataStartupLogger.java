// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.appmetadata;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BuildMetadataStartupLogger {

  private static final Logger log = LoggerFactory.getLogger(BuildMetadataStartupLogger.class);

  private final BuildMetadata buildMetadata;

  public BuildMetadataStartupLogger(BuildMetadata buildMetadata) {
    this.buildMetadata = buildMetadata;
  }

  @PostConstruct
  public void logBuildMetadata() {
    var parts = new ArrayList<>(List.of("Build metadata:"));
    appendField(parts, "appVersion", buildMetadata.appVersion());
    appendField(parts, "revision", buildMetadata.revision());
    appendField(parts, "branch", buildMetadata.branch());
    appendField(parts, "buildDate", buildMetadata.buildDate());

    log.info(String.join(" ", parts));
  }

  private void appendField(List<String> parts, String key, @Nullable String value) {
    parts.add(key + "=" + (value == null ? "unset" : value));
  }
}
