// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket.rtsp.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.opencop.api.connect.technical.config.RtspConnectorDefaults;
import com.opencop.api.connect.technical.gateway.socket.rtsp.JavaCvRtspKlvExtractor;
import com.opencop.api.connect.technical.gateway.socket.rtsp.KlvPacketFramer;
import com.opencop.api.connect.technical.gateway.socket.rtsp.RtspKlvExtractorException;
import com.opencop.core.sdk.model.connection.spec.transport.RtspConfig;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class JavaCvRtspKlvExtractorIT {

  @Test
  void startWrapsJavaCvErrorsWithoutUrlTelemetry() throws Exception {
    var config = new RtspConfig("rtsp://127.0.0.1:1/missing", null);
    var extractor =
        extractorFor(
            "rtsp://user:pass@127.0.0.1:1/missing?token=secret",
            config,
            new RtspConnectorDefaults(Duration.ofMillis(250), Duration.ofMillis(250), true));

    var thrown = catchThrowable(extractor::start);

    assertThat(thrown)
        .isInstanceOf(RtspKlvExtractorException.class)
        .hasMessage("Failed to open RTSP KLV stream");
    assertThat(stackTrace(thrown))
        .doesNotContain("rtsp://")
        .doesNotContain("rtsps://")
        .doesNotContain("user")
        .doesNotContain("pass")
        .doesNotContain("secret")
        .doesNotContain("127.0.0.1")
        .doesNotContain("missing")
        .doesNotContain("token");
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "KLV_FIXTURE_PATH", matches = ".+")
  void fixtureExtractionMatchesKnownKlvBaseline() throws Exception {
    var path = Path.of(System.getenv("KLV_FIXTURE_PATH"));
    var extractor =
        extractorFor(
            path.toString(),
            new RtspConfig("rtsp://localhost:8554/fixture", null),
            new RtspConnectorDefaults(Duration.ofSeconds(3), Duration.ofSeconds(5), true));
    var framer = new KlvPacketFramer();
    var bytes = new ByteArrayOutputStream();
    var unitCount = 0;

    try (extractor) {
      extractor.start();
      byte[] packet;
      while ((packet = extractor.readNextPacket(Long.MAX_VALUE)) != null) {
        for (var unit : framer.append(packet)) {
          bytes.writeBytes(unit);
          unitCount++;
        }
      }
    }

    assertThat(bytes.size()).isEqualTo(977);
    assertThat(sha256(bytes.toByteArray()))
        .isEqualTo("a810e4b60ff33b1bdc1831594201d8158655c0808bdef1b22d84a9eb26e22e51");
    assertThat(unitCount).isEqualTo(6);
  }

  private static String sha256(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  private static JavaCvRtspKlvExtractor extractorFor(
      String input, RtspConfig config, RtspConnectorDefaults connectorDefaults) throws Exception {
    var constructor =
        JavaCvRtspKlvExtractor.class.getDeclaredConstructor(
            String.class, RtspConfig.class, RtspConnectorDefaults.class);
    constructor.setAccessible(true);
    return constructor.newInstance(input, config, connectorDefaults);
  }

  private static String stackTrace(Throwable throwable) {
    var writer = new StringWriter();
    throwable.printStackTrace(new PrintWriter(writer));
    return writer.toString();
  }
}
