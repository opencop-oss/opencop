// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.connection.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.business.model.profile.DelimiterModes;
import com.opencop.core.sdk.model.connection.add.SecurityKind;
import com.opencop.core.sdk.model.connection.spec.security.KeystoreRef;
import com.opencop.core.sdk.model.connection.spec.security.MtlsConfig;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.CustomTcpFraming;
import com.opencop.core.sdk.model.connection.spec.transport.IntegrationTcpFraming;
import com.opencop.core.sdk.model.connection.spec.transport.RtspConfig;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.connection.spec.transport.TransportConfig;
import com.opencop.core.sdk.model.connection.spec.transport.UdpConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

class ConnectionSpecTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void readsTcpSecurityFromUnderItsTransport() {
    var json =
        """
        {
          "transport": {
            "type": "tcp",
            "host": "tak.example.test",
            "port": 8089,
            "connectTimeoutMs": 3000,
            "readTimeoutMs": 15000,
            "tcpNoDelay": true,
            "security": {
              "type": "mtls",
              "keyStore": {"uri": "secrets/client", "type": "PKCS12"},
              "trustStore": {"uri": "secrets/truststore", "type": "PKCS12"},
              "enabledCiphers": null,
              "enabledProtocols": null,
              "hostnameVerification": false
            }
          }
        }
        """;

    var spec = objectMapper.readValue(json, ConnectionSpec.class);

    var tcp = (TcpConfig) spec.transport();
    assertThat(tcp.security())
        .isEqualTo(
            new MtlsConfig(
                null,
                null,
                false,
                new KeystoreRef("secrets/truststore", "PKCS12"),
                new KeystoreRef("secrets/client", "PKCS12")));
    assertThat(spec.securityType()).isEqualTo(SecurityKind.MTLS);
  }

  @Test
  void anMtlsSpecHoldsItsClientStoreAndTruststore() {
    assertThat(mtlsSpec("secrets/client", "secrets/truststore").secretRefs())
        .containsExactly("secrets/client", "secrets/truststore");
  }

  /** A client-only connection keeps a truststore reference without a URI, which holds nothing. */
  @Test
  void aClientOnlyMtlsSpecHoldsItsClientStoreAlone() {
    assertThat(mtlsSpec("secrets/client", null).secretRefs()).containsExactly("secrets/client");
  }

  @Test
  void aSpecWithoutSecretsHoldsNone() {
    assertThat(
            new ConnectionSpec(
                    new TcpConfig(
                        "tak.example.test", 8089, null, null, null, null, new NoSecurity()))
                .secretRefs())
        .isEmpty();
    assertThat(new ConnectionSpec(new UdpConfig("127.0.0.1", 9000, 9000)).secretRefs()).isEmpty();
    assertThat(new ConnectionSpec(new RtspConfig("rtsp://camera/stream", null)).secretRefs())
        .isEmpty();
  }

  /** The refs are derived from the stored fields, so storing them as well would duplicate them. */
  @Test
  void neverWritesTheRefsItHoldsIntoTheStoredSpec() {
    var json = objectMapper.writeValueAsString(mtlsSpec("secrets/client", "secrets/truststore"));

    assertThat(json).doesNotContain("secretRefs");
    assertThat(objectMapper.readValue(json, ConnectionSpec.class))
        .isEqualTo(mtlsSpec("secrets/client", "secrets/truststore"));
  }

  @Test
  void rejectsATcpSpecWithoutSecurity() {
    var json =
        """
        {"transport": {"type": "tcp", "host": "tak.example.test", "port": 8089}}
        """;

    assertThatThrownBy(() -> objectMapper.readValue(json, ConnectionSpec.class))
        .isInstanceOf(JacksonException.class)
        .hasMessageContaining("security");
  }

  @Test
  void rejectsATcpSecurityNoTcpConnectorRuns() {
    var json =
        """
        {
          "transport": {
            "type": "tcp",
            "host": "tak.example.test",
            "port": 8089,
            "security": {"type": "tls"}
          }
        }
        """;

    assertThatThrownBy(() -> objectMapper.readValue(json, ConnectionSpec.class))
        .isInstanceOf(JacksonException.class)
        .hasMessageContaining("tls");
  }

  @Test
  void reportsNoSecurityForTransportsWithoutOne() {
    assertThat(new ConnectionSpec(new UdpConfig("0.0.0.0", 9000, null)).securityType())
        .isEqualTo(SecurityKind.NONE);
    assertThat(
            new ConnectionSpec(new RtspConfig("rtsp://camera.example.test/stream", null))
                .securityType())
        .isEqualTo(SecurityKind.NONE);
    assertThat(
            new ConnectionSpec(
                    new TcpConfig(
                        "tak.example.test", 8089, 3000, 15000, true, null, new NoSecurity()))
                .securityType())
        .isEqualTo(SecurityKind.NONE);
  }

  @Test
  void storesTcpFramingAsAnIntegrationReferenceOrAnInlineDelimiter() {
    var framingId = UUID.fromString("d006c0ad-f84a-4e69-b7cb-3f58bcf4ecb6");
    var integration =
        new ConnectionSpec(
            new TcpConfig(
                "tak.example.test",
                8089,
                3000,
                15000,
                true,
                new IntegrationTcpFraming(framingId),
                new NoSecurity()));
    var custom =
        new ConnectionSpec(
            new TcpConfig(
                "tak.example.test",
                8089,
                3000,
                15000,
                true,
                new CustomTcpFraming(DelimiterModes.DELIMITER_SPLIT, "\n"),
                new NoSecurity()));

    var integrationJson = objectMapper.readTree(objectMapper.writeValueAsString(integration));
    var customJson = objectMapper.readTree(objectMapper.writeValueAsString(custom));

    assertThat(integrationJson.at("/transport/framing/kind").asString()).isEqualTo("integration");
    assertThat(integrationJson.at("/transport/framing/framingId").asString())
        .isEqualTo(framingId.toString());
    assertThat(integrationJson.at("/transport/security/type").asString()).isEqualTo("none");
    assertThat(customJson.at("/transport/framing/kind").asString()).isEqualTo("custom");
    assertThat(customJson.at("/transport/framing/mode").asString()).isEqualTo("delimiterSplit");
    assertThat(customJson.at("/transport/framing/delimiter").asString()).isEqualTo("\n");
    assertThat(objectMapper.readValue(integrationJson.toString(), ConnectionSpec.class))
        .isEqualTo(integration);
    assertThat(objectMapper.readValue(customJson.toString(), ConnectionSpec.class))
        .isEqualTo(custom);
  }

  @Test
  void rejectsStoredCustomFramingWithAnUnsupportedMode() {
    var json =
        """
        {
          "transport": {
            "type": "tcp",
            "host": "tak.example.test",
            "port": 8089,
            "framing": {"kind": "custom", "mode": "lengthPrefixed", "delimiter": ""},
            "security": {"type": "none"}
          }
        }
        """;

    assertThatThrownBy(() -> objectMapper.readValue(json, ConnectionSpec.class))
        .isInstanceOf(JacksonException.class)
        .hasMessageContaining("lengthPrefixed");
  }

  @Test
  void storesOnlyTheTransportAtTheRootOfTheSpec() {
    List<TransportConfig> transports =
        List.of(
            new TcpConfig("tak.example.test", 8089, 3000, 15000, true, null, new NoSecurity()),
            new UdpConfig("0.0.0.0", 9000, null),
            new RtspConfig("rtsp://camera.example.test/stream", null));

    for (var transport : transports) {
      var json =
          objectMapper.readTree(objectMapper.writeValueAsString(new ConnectionSpec(transport)));

      assertThat(json.propertyNames()).as(transport.toString()).containsExactly("transport");
      assertThat(json.at("/transport").has("kind")).as(transport.toString()).isFalse();
    }
  }

  private static ConnectionSpec mtlsSpec(String clientRef, String truststoreRef) {
    return new ConnectionSpec(
        new TcpConfig(
            "tak.example.test",
            8089,
            null,
            null,
            null,
            null,
            new MtlsConfig(
                null,
                null,
                false,
                new KeystoreRef(truststoreRef, "PKCS12"),
                new KeystoreRef(clientRef, "PKCS12"))));
  }
}
