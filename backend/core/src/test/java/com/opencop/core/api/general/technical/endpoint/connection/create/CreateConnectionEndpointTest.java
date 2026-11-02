// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.connection.create;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.general.business.model.connection.create.ConnectionCreation;
import com.opencop.core.api.general.business.model.connection.create.ConnectionCreationResult;
import com.opencop.core.api.general.business.model.connection.create.CreationReceipt;
import com.opencop.core.api.general.business.model.connection.create.TcpFramingSelection;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.api.general.ports.usecase.connection.create.CreateConnection;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputResource;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputValue;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.model.connection.ConnectionInfo;
import com.opencop.core.sdk.model.connection.ConnectionSettingsStatus;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.ports.gateway.kafka.KafkaCredentials;
import com.opencop.core.sdk.ports.gateway.kafka.Password;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputException;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputFailure;
import com.opencop.core.sdk.technical.exception.GlobalExceptionManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

class CreateConnectionEndpointTest {

  private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
  private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-4000-8000-0000000000d1");
  private static final UUID INTEGRATION_ID =
      UUID.fromString("5b96caf4-20dd-4a8a-8f1b-09fb594265be");
  private static final UUID EXTRACTOR_ID = UUID.fromString("3f1d7a52-8c4e-4b0a-9e6f-2d5b7c8a1e93");
  private static final UUID MAPPER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
  private static final String PKCS12 = "application/x-pkcs12";
  private static final String LINES =
      "{\"type\": \"CUSTOM\", \"mode\": \"delimiterSplit\", \"delimiter\": \"|\"}";

  private final CreateConnection createConnection = mock(CreateConnection.class);

  /**
   * The application's mapper ignores unknown fields, so the endpoint is built on one that does too:
   * a rejection then proves the endpoint's own strictness, not the test's mapper.
   */
  private final MockMvc mockMvc =
      MockMvcBuilders.standaloneSetup(
              new CreateConnectionEndpoint(
                  new CreateConnectionRequestReader(
                      JsonMapper.builder()
                          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                          .build()),
                  createConnection))
          .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
          .setControllerAdvice(new GlobalExceptionManager())
          .build();

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void createsTheConnectionTheRequestDescribesAndAnswersWithIt() throws Exception {
    when(createConnection.create(any()))
        .thenReturn(new ConnectionCreationResult(connection(), new CreationReceipt.Udp()));

    mockMvc
        .perform(request(udp("")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.kind").value("UDP"))
        .andExpect(jsonPath("$.id").value(SOURCE_ID.toString()))
        .andExpect(jsonPath("$.name").value("Alpha UDP"))
        .andExpect(jsonPath("$.state").value("CONNECTED"));

    var creation = ArgumentCaptor.forClass(ConnectionCreation.class);
    verify(createConnection).create(creation.capture());
    assertThat(creation.getValue()).isInstanceOf(ConnectionCreation.Udp.class);
    var details = creation.getValue().details();
    assertThat(details.name()).isEqualTo("Alpha UDP");
    assertThat(details.access()).isEqualTo("R");
    assertThat(details.integration())
        .isEqualTo(new IntegrationConnectionSchemaReference(INTEGRATION_ID, 1));
    assertThat(details.extractorId()).isEqualTo(EXTRACTOR_ID);
    assertThat(details.ingressMapperId()).isEqualTo(MAPPER_ID);
    assertThat(details.requestedBy().userId()).isEqualTo(USER_ID);
    assertThat(details.input().values())
        .extracting(ConnectionInputValue::name)
        .containsExactlyInAnyOrder("host", "listenPort");
    assertThat(details.input().value("listenPort")).contains("8087");
  }

  @Test
  void passesTheSettingsOnAsTheRequestSentThem() throws Exception {
    when(createConnection.create(any()))
        .thenReturn(new ConnectionCreationResult(connection(), new CreationReceipt.Udp()));

    mockMvc
        .perform(
            request(
                udp(
                    """
                    , "settings": {"presence": {"callsign": "Alpha", "latitude": 45.5},
                                   "heartbeat": {"pingEnabled": false, "pingThreshold": "PT45S"}}                    """)))
        .andExpect(status().isCreated());

    var creation = ArgumentCaptor.forClass(ConnectionCreation.class);
    verify(createConnection).create(creation.capture());
    assertThat(creation.getValue().details().settings())
        .isEqualTo(
            Map.of(
                "presence", Map.of("callsign", "Alpha", "latitude", 45.5),
                "heartbeat", Map.of("pingEnabled", false, "pingThreshold", "PT45S")));
  }

  @Test
  void anOldRequestShapeSendsNoSettings() throws Exception {
    when(createConnection.create(any()))
        .thenReturn(new ConnectionCreationResult(connection(), new CreationReceipt.Udp()));

    mockMvc.perform(request(udp(""))).andExpect(status().isCreated());

    var creation = ArgumentCaptor.forClass(ConnectionCreation.class);
    verify(createConnection).create(creation.capture());
    assertThat(creation.getValue().details().settings()).isNull();
  }

  /** Null settings mean no overrides, as on retrieve, so they are read as if omitted. */
  @Test
  void nullSettingsAreReadAsOmitted() throws Exception {
    when(createConnection.create(any()))
        .thenReturn(new ConnectionCreationResult(connection(), new CreationReceipt.Udp()));

    mockMvc.perform(request(udp(", \"settings\": null"))).andExpect(status().isCreated());

    var creation = ArgumentCaptor.forClass(ConnectionCreation.class);
    verify(createConnection).create(creation.capture());
    assertThat(creation.getValue().details().settings()).isNull();
  }

  @Test
  void readsTheSettingsFromTheMultipartBody() throws Exception {
    when(createConnection.create(any()))
        .thenReturn(new ConnectionCreationResult(connection(), new CreationReceipt.Tcp()));
    var body =
        tcp(LINES)
            .replaceFirst("\\}\\s*$", ", \"settings\": {\"presence\": {\"callsign\": \"Alpha\"}}}");

    mockMvc.perform(multipartRequest(body)).andExpect(status().isCreated());

    var creation = ArgumentCaptor.forClass(ConnectionCreation.class);
    verify(createConnection).create(creation.capture());
    assertThat(creation.getValue().details().settings())
        .isEqualTo(Map.of("presence", Map.of("callsign", "Alpha")));
  }

  /** A Kafka producer's credentials are shown once, in the answer to its creation. */
  @Test
  void answersAKafkaCreationWithItsOneTimeCredentials() throws Exception {
    var credentials =
        new KafkaCredentials("source.topic", "producer-user", new Password("secret"), "kafka:9092");
    when(createConnection.create(any()))
        .thenReturn(
            new ConnectionCreationResult(connection(), new CreationReceipt.Kafka(credentials)));

    mockMvc
        .perform(request(udp("").replace("\"UDP\"", "\"KAFKA\"")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(SOURCE_ID.toString()))
        .andExpect(jsonPath("$.kind").value("KAFKA"))
        .andExpect(jsonPath("$.kafkaCredentials.topicName").value("source.topic"))
        .andExpect(jsonPath("$.kafkaCredentials.username").value("producer-user"));

    var creation = ArgumentCaptor.forClass(ConnectionCreation.class);
    verify(createConnection).create(creation.capture());
    assertThat(creation.getValue()).isInstanceOf(ConnectionCreation.Kafka.class);
  }

  @Test
  void answersEveryOtherKindWithoutCredentials() throws Exception {
    when(createConnection.create(any()))
        .thenReturn(new ConnectionCreationResult(connection(), new CreationReceipt.Rtsp()));

    mockMvc
        .perform(request(udp("").replace("\"UDP\"", "\"RTSP\"")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.kind").value("RTSP"))
        .andExpect(jsonPath("$.kafkaCredentials").doesNotExist());

    var creation = ArgumentCaptor.forClass(ConnectionCreation.class);
    verify(createConnection).create(creation.capture());
    assertThat(creation.getValue()).isInstanceOf(ConnectionCreation.Rtsp.class);
  }

  /** The delimiter is handed on exactly as sent, escape sequences included. */
  @Test
  void createsATcpConnectionWithTheFramingItSelects() throws Exception {
    when(createConnection.create(any()))
        .thenReturn(new ConnectionCreationResult(connection(), new CreationReceipt.Tcp()));

    mockMvc
        .perform(
            request(
                tcp(
                    "{\"type\": \"CUSTOM\", \"mode\": \"delimiterSplit\", \"delimiter\": \"\\\\n\"}")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.kind").value("TCP"));

    var creation = ArgumentCaptor.forClass(ConnectionCreation.class);
    verify(createConnection).create(creation.capture());
    assertThat(((ConnectionCreation.Tcp) creation.getValue()).framing())
        .isEqualTo(new TcpFramingSelection.Custom("delimiterSplit", "\\n"));
  }

  @Test
  void passesTheEgressMapperOnForATcpConnection() throws Exception {
    var egressMapperId = UUID.randomUUID();
    when(createConnection.create(any()))
        .thenReturn(new ConnectionCreationResult(connection(), new CreationReceipt.Tcp()));

    mockMvc
        .perform(
            request(
                tcp(LINES)
                    .replace(
                        "\"framing\":",
                        "\"egressMapperId\": \"%s\", \"framing\":".formatted(egressMapperId))))
        .andExpect(status().isCreated());

    var creation = ArgumentCaptor.forClass(ConnectionCreation.class);
    verify(createConnection).create(creation.capture());
    assertThat(((ConnectionCreation.Tcp) creation.getValue()).egressMapperId())
        .isEqualTo(egressMapperId);
  }

  @Test
  void passesAnIntegrationFramingOnByItsIdentifier() throws Exception {
    var framingId = UUID.randomUUID();
    when(createConnection.create(any()))
        .thenReturn(new ConnectionCreationResult(connection(), new CreationReceipt.Tcp()));

    mockMvc
        .perform(
            request(tcp("{\"type\": \"INTEGRATION\", \"framingId\": \"%s\"}".formatted(framingId))))
        .andExpect(status().isCreated());

    var creation = ArgumentCaptor.forClass(ConnectionCreation.class);
    verify(createConnection).create(creation.capture());
    assertThat(((ConnectionCreation.Tcp) creation.getValue()).framing())
        .isEqualTo(new TcpFramingSelection.Integration(framingId));
  }

  /** Only a TCP connection writes, so no other kind takes an egress mapper. */
  @Test
  void rejectsAnEgressMapperOnAKindThatOnlyReads() throws Exception {
    var egress = ", \"egressMapperId\": \"%s\"".formatted(UUID.randomUUID());
    for (var kind : List.of("UDP", "RTSP", "KAFKA")) {
      assertUnknownField(udp(egress).replace("\"UDP\"", "\"" + kind + "\""), "egressMapperId");
    }
  }

  @Test
  void rejectsAFramingFieldOnAKindThatDoesNotFrame() throws Exception {
    assertUnknownField(udp(", \"framing\": {\"type\": \"CUSTOM\"}"), "framing");
  }

  /** Each file part supplies the extractor field it is named after; a repeated name, a list. */
  @Test
  void createsFromAMultipartRequestWithItsFilesNamedAfterTheirFields() throws Exception {
    when(createConnection.create(any()))
        .thenReturn(new ConnectionCreationResult(connection(), new CreationReceipt.Tcp()));

    mockMvc
        .perform(
            multipartRequest(tcp(LINES))
                .file(new MockMultipartFile("keyStore", "client.p12", PKCS12, new byte[] {1}))
                .file(new MockMultipartFile("anchors", "first.pem", "text/plain", new byte[] {2}))
                .file(new MockMultipartFile("anchors", "second.pem", "text/plain", new byte[] {3})))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.kind").value("TCP"));

    var creation = ArgumentCaptor.forClass(ConnectionCreation.class);
    verify(createConnection).create(creation.capture());
    var input = creation.getValue().details().input();
    assertThat(input.value("host")).contains("feed.example.test");
    assertThat(input.resources("keyStore"))
        .extracting(ConnectionInputResource::filename)
        .containsExactly("client.p12");
    assertThat(input.resources("anchors"))
        .extracting(ConnectionInputResource::filename)
        .containsExactly("first.pem", "second.pem");
  }

  @Test
  void readsTheJsonFromAFilePartNamedBody() throws Exception {
    when(createConnection.create(any()))
        .thenReturn(new ConnectionCreationResult(connection(), new CreationReceipt.Tcp()));
    var body =
        new MockMultipartFile(
            "body", "body.json", MediaType.APPLICATION_JSON_VALUE, tcp(LINES).getBytes(UTF_8));

    mockMvc.perform(multipart().file(body)).andExpect(status().isCreated());

    var creation = ArgumentCaptor.forClass(ConnectionCreation.class);
    verify(createConnection).create(creation.capture());
    assertThat(creation.getValue().details().name()).isEqualTo("Feed");
  }

  @ParameterizedTest
  @ValueSource(strings = {"5", "\"presence\"", "[]"})
  void rejectsSettingsThatAreNotAnObject(String settings) throws Exception {
    mockMvc
        .perform(request(udp(", \"settings\": " + settings)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(ConnectionExceptionCodes.INVALID_REQUEST_BODY.code()));
    verifyNoInteractions(createConnection);
  }

  @Test
  void rejectsMultipartSettingsThatAreNotAnObject() throws Exception {
    var body = tcp(LINES).replaceFirst("\\}\\s*$", ", \"settings\": [\"presence\"]}");

    mockMvc
        .perform(multipartRequest(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(ConnectionExceptionCodes.INVALID_REQUEST_BODY.code()));
    verifyNoInteractions(createConnection);
  }

  @Test
  void rejectsASecondPartNamedBody() throws Exception {
    mockMvc
        .perform(
            multipartRequest(tcp(LINES))
                .file(
                    new MockMultipartFile(
                        "body", "other.json", "application/json", new byte[] {1})))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("DUPLICATE_REQUEST_BODY"))
        .andExpect(jsonPath("$.field").value("body"));
    verifyNoInteractions(createConnection);
  }

  @Test
  void rejectsAMultipartRequestWithoutItsJson() throws Exception {
    mockMvc
        .perform(
            multipart()
                .file(new MockMultipartFile("keyStore", "client.p12", PKCS12, new byte[] {1})))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    verifyNoInteractions(createConnection);
  }

  /** A value belongs in the JSON, so a text part under any other name is not quietly ignored. */
  @Test
  void rejectsATextPartOtherThanTheJson() throws Exception {
    mockMvc
        .perform(
            multipartRequest(tcp(LINES))
                .part(new MockPart("clientPassword", "secret".getBytes(UTF_8))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("UNKNOWN_REQUEST_FIELD"))
        .andExpect(jsonPath("$.field").value("clientPassword"));
    verifyNoInteractions(createConnection);
  }

  /**
   * Only a part carries the JSON; a query parameter of the same name neither does nor doubles it.
   */
  @Test
  void ignoresAQueryParameterNamedBody() throws Exception {
    when(createConnection.create(any()))
        .thenReturn(new ConnectionCreationResult(connection(), new CreationReceipt.Tcp()));

    mockMvc
        .perform(multipartRequest(tcp(LINES)).queryParam("body", "{}"))
        .andExpect(status().isCreated());
    mockMvc
        .perform(multipart().queryParam("body", tcp(LINES)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  /** A field is either a value or a file, so a name supplied as both is ambiguous. */
  @Test
  void rejectsANameSuppliedBothAsAValueAndAsAFile() throws Exception {
    mockMvc
        .perform(
            multipartRequest(tcp(LINES))
                .file(new MockMultipartFile("host", "host.txt", "text/plain", new byte[] {1})))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CONNECTION_INPUT_NAME_CONFLICT"))
        .andExpect(jsonPath("$.field").value("host"));
    mockMvc
        .perform(
            multipartRequest(
                    tcp(LINES)
                        .replace("\"port\": \"8089\"", "\"port\": \"8089\", \"keyStore\": null"))
                .file(new MockMultipartFile("keyStore", "client.p12", PKCS12, new byte[] {1})))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CONNECTION_INPUT_NAME_CONFLICT"))
        .andExpect(jsonPath("$.field").value("keyStore"));
    verifyNoInteractions(createConnection);
  }

  @Test
  void rejectsABlankFileOrValueName() throws Exception {
    mockMvc
        .perform(
            multipartRequest(tcp(LINES))
                .file(new MockMultipartFile(" ", "client.p12", PKCS12, new byte[] {1})))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BLANK_CONNECTION_INPUT_NAME"));
    mockMvc
        .perform(request(udp("").replace("\"host\":", "\" \":")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BLANK_CONNECTION_INPUT_NAME"));
    verifyNoInteractions(createConnection);
  }

  /** A UDP stream is not framed and a UDP connection only reads. */
  @Test
  void rejectsFieldsBelongingToAnotherKind() throws Exception {
    assertUnknownField(udp(", \"framing\": {\"type\": \"CUSTOM\"}"), "framing");
    assertUnknownField(
        udp(", \"egressMapperId\": \"" + UUID.randomUUID() + "\""), "egressMapperId");
  }

  @Test
  void rejectsAnUnknownFieldInsideANestedObject() throws Exception {
    var body =
        """
        {"kind": "UDP", "name": "Alpha UDP", "access": "R",
         "integration": {"id": "%s", "schemaVersion": 1, "alias": "native"},
         "extractorId": "%s", "values": {}, "mapperId": "%s"}
        """
            .formatted(INTEGRATION_ID, EXTRACTOR_ID, MAPPER_ID);

    assertUnknownField(body, "integration.alias");
  }

  @Test
  void rejectsAKindItDoesNotSupportOrAMissingKind() throws Exception {
    for (var body :
        new String[] {
          udp("").replace("\"UDP\"", "\"SCTP\""), udp("").replace("\"kind\": \"UDP\",", "")
        }) {
      mockMvc
          .perform(request(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("UNSUPPORTED_CONNECTION_KIND"))
          .andExpect(jsonPath("$.field").value("kind"));
    }
    verifyNoInteractions(createConnection);
  }

  /** The kind was sent correctly, so the client is pointed at the framing's own type instead. */
  @Test
  void rejectsAFramingWithoutAKnownTypeNamingTheFramingType() throws Exception {
    for (var framing :
        new String[] {
          "{\"mode\": \"delimiterSplit\", \"delimiter\": \";\"}",
          "{\"type\": \"LINES\", \"mode\": \"delimiterSplit\", \"delimiter\": \";\"}"
        }) {
      mockMvc
          .perform(request(tcp(framing)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("REQUIRED_FIELD_INVALID"))
          .andExpect(jsonPath("$.field").value("framing.type"));
    }
    verifyNoInteractions(createConnection);
  }

  @Test
  void rejectsABodyThatIsNotJsonOrDescribesNoConnection() throws Exception {
    for (var body : new String[] {"{not json", "null"}) {
      mockMvc
          .perform(request(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
    verifyNoInteractions(createConnection);
  }

  @Test
  void passesAnIncompleteIntegrationSelectionOnAsNone() throws Exception {
    when(createConnection.create(any()))
        .thenReturn(new ConnectionCreationResult(connection(), new CreationReceipt.Udp()));
    var body = udp("").replace("\"schemaVersion\": 1", "\"schemaVersion\": 0");

    mockMvc.perform(request(body)).andExpect(status().isCreated());

    var creation = ArgumentCaptor.forClass(ConnectionCreation.class);
    verify(createConnection).create(creation.capture());
    assertThat(creation.getValue().details().integration()).isNull();
  }

  /** The requester fixes their own input, so they learn the reason and the field it concerns. */
  @Test
  void answersAnInputRejectionWithItsDetailAndField() throws Exception {
    when(createConnection.create(any()))
        .thenThrow(
            ConnectionManagementException.inputRejected(
                ConnectionInputException.rejecting(
                    "listenPort",
                    ConnectionInputFailure.INVALID_INPUT,
                    "INVALID_UDP_PORT",
                    "Listen port must be between 1 and 65535")));

    mockMvc
        .perform(request(udp("")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CONNECTION_INPUT_INVALID"))
        .andExpect(jsonPath("$.message").value("Listen port must be between 1 and 65535"))
        .andExpect(jsonPath("$.detailCode").value("INVALID_UDP_PORT"))
        .andExpect(jsonPath("$.field").value("listenPort"));
  }

  private void assertUnknownField(String body, String field) throws Exception {
    mockMvc
        .perform(request(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("UNKNOWN_REQUEST_FIELD"))
        .andExpect(jsonPath("$.field").value(field));
    verifyNoInteractions(createConnection);
  }

  private MockHttpServletRequestBuilder request(String body) {
    return post("/v2/connections")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        .with(securityContext(TestAuthentications.oidcUser(USER_ID)));
  }

  private MockMultipartHttpServletRequestBuilder multipart() {
    var builder = MockMvcRequestBuilders.multipart("/v2/connections");
    builder.with(securityContext(TestAuthentications.oidcUser(USER_ID)));
    return builder;
  }

  private MockMultipartHttpServletRequestBuilder multipartRequest(String body) {
    var builder = multipart();
    builder.part(new MockPart(CreateConnectionMultipart.BODY_PART, body.getBytes(UTF_8)));
    return builder;
  }

  private static String tcp(String framing) {
    return """
        {"kind": "TCP", "name": "Feed", "access": "R",
         "integration": {"id": "%s", "schemaVersion": 1},
         "extractorId": "%s", "values": {"host": "feed.example.test", "port": "8089"},
         "mapperId": "%s", "framing": %s}
        """
        .formatted(INTEGRATION_ID, EXTRACTOR_ID, MAPPER_ID, framing);
  }

  private static String udp(String extraFields) {
    return """
        {"kind": "UDP", "name": "Alpha UDP", "access": "R",
         "integration": {"id": "%s", "schemaVersion": 1},
         "extractorId": "%s", "values": {"host": "0.0.0.0", "listenPort": "8087"},
         "mapperId": "%s"%s}
        """
        .formatted(INTEGRATION_ID, EXTRACTOR_ID, MAPPER_ID, extraFields);
  }

  private static ConnectionInfo connection() {
    return new ConnectionInfo(
        SOURCE_ID,
        "Alpha UDP",
        new IntegrationConnectionSchemaReference(INTEGRATION_ID, 1),
        ConnectorState.CONNECTED,
        null,
        TransportKind.UDP,
        null,
        null,
        USER_ID,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        "connect-1",
        true,
        AccessMode.READ,
        null,
        null,
        ConnectionSettingsStatus.NONE,
        List.of(),
        null);
  }

  private static RequestPostProcessor securityContext(Authentication auth) {
    return request -> {
      var context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(auth);
      SecurityContextHolder.setContext(context);
      return request;
    };
  }
}
