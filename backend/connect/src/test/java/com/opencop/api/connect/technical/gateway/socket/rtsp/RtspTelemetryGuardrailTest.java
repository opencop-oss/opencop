// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket.rtsp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RtspTelemetryGuardrailTest {

  private static final List<String> PRODUCTION_RTSP_TELEMETRY_FILES =
      List.of(
          "com/opencop/api/connect/technical/gateway/socket/RtspKlvConnector.java",
          "com/opencop/api/connect/technical/gateway/socket/rtsp/JavaCvRtspKlvExtractor.java",
          "com/opencop/api/connect/technical/observability/rtsp/RtspKlvObservability.java");

  private static final List<String> SENSITIVE_URL_TOKENS =
      List.of("rtsp://", "rtsps://", "streamUrl", "password", "userInfo");

  @Test
  void rtspTelemetryFilesDoNotEmbedSensitiveUrlTokens() throws IOException {
    var sourceRoot = sourceRoot();

    for (var relativeFile : PRODUCTION_RTSP_TELEMETRY_FILES) {
      var content = Files.readString(sourceRoot.resolve(relativeFile));

      assertThat(content).doesNotContain(SENSITIVE_URL_TOKENS.toArray(String[]::new));
    }
  }

  @Test
  void rtspTelemetryFilesDoNotRecordExceptionEventsOrRawMessages() throws IOException {
    var sourceRoot = sourceRoot();
    var unsafeLines = new ArrayList<String>();

    for (var relativeFile : PRODUCTION_RTSP_TELEMETRY_FILES) {
      var file = sourceRoot.resolve(relativeFile);
      var content = Files.readString(file);
      assertThat(content).doesNotContain(".recordException(");

      var lines = Files.readAllLines(file);
      for (int index = 0; index < lines.size(); index++) {
        var line = lines.get(index);
        if (isTelemetryOrLogLine(line) && line.contains("getMessage()")) {
          unsafeLines.add(relativeFile + ":" + (index + 1) + " " + line.trim());
        }
      }
    }

    assertThat(unsafeLines).isEmpty();
  }

  private static boolean isTelemetryOrLogLine(String line) {
    return line.contains("log.")
        || line.contains("span.")
        || line.contains("meterRegistry")
        || line.contains("Timer.")
        || line.contains("DistributionSummary.")
        || line.contains("Gauge.");
  }

  private static Path sourceRoot() {
    var base = Path.of("").toAbsolutePath();
    var candidates = List.of(base.resolve("src/main/java"), base.resolve("connect/src/main/java"));
    return candidates.stream()
        .filter(Files::isDirectory)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Could not locate connect source root"));
  }
}
