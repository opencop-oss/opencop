// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.helper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Resolves the runtime instance ID for a service. Resolution order:
 *
 * <ol>
 *   <li>{@code OPENCOP_INSTANCE_ID} env var — explicit override for named instances
 *   <li>{@code HOSTNAME} env var (Docker only) — prefixed with serviceName for readability, e.g.
 *       {@code connect-a1b2c3d4e5f6}
 *   <li>{@code <serviceName>-<random8>} — local dev fallback, unique per JVM
 * </ol>
 */
public final class InstanceIdentity {

  private InstanceIdentity() {}

  public static String resolveInstanceId(String serviceName) {
    String id = System.getenv("OPENCOP_INSTANCE_ID");
    if (id != null && !id.isBlank()) return id;

    // In Docker, HOSTNAME is the container short ID — stable across restarts of the same
    // container (only changes on recreate). Traceable via `docker ps` / `docker inspect`.
    // Outside Docker, HOSTNAME is the machine name (e.g. "alice-MacBook-Pro.local") which
    // would collide across multiple local instances, so we skip it and use a random suffix.
    if (isRunningInDocker()) {
      id = System.getenv("HOSTNAME");
      if (id != null && !id.isBlank()) return serviceName + "-" + id;
    }

    return serviceName + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private static boolean isRunningInDocker() {
    return Files.exists(Path.of("/.dockerenv"));
  }
}
