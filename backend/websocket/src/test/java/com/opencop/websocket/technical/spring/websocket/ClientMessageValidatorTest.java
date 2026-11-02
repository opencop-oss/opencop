// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import com.google.protobuf.UnknownFieldSet;
import java.util.stream.Stream;
import opencop.core.proto.websocket.SocketProtos.SocketMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ClientMessageValidatorTest {

  @Test
  void appendsErrorWithoutChangingExistingWireNumbers() {
    assertThat(SocketMessage.Type.UNKNOWN.getNumber()).isZero();
    assertThat(SocketMessage.Type.CONNECT.getNumber()).isEqualTo(1);
    assertThat(SocketMessage.Type.SUBSCRIBE.getNumber()).isEqualTo(2);
    assertThat(SocketMessage.Type.SEND.getNumber()).isEqualTo(3);
    assertThat(SocketMessage.Type.UNSUBSCRIBE.getNumber()).isEqualTo(4);
    assertThat(SocketMessage.Type.HEARTBEAT.getNumber()).isEqualTo(5);
    assertThat(SocketMessage.Type.ERROR.getNumber()).isEqualTo(6);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/user/queue/history",
        "/user/queue/messages",
        "/user/queue/notification",
        "/topic/queue/event/4b0da4e4-7a7f-45f1-8bf9-575f6ba5a76c",
        "/topic/watchbox/4b0da4e4-7a7f-45f1-8bf9-575f6ba5a76c"
      })
  void acceptsEverySupportedSubscribeDestination(String destination) {
    var validator = new ClientMessageValidator();
    var message =
        SocketMessage.newBuilder()
            .setType(SocketMessage.Type.SUBSCRIBE)
            .setDestination(destination)
            .build();

    assertThat(validator.validate(message)).isInstanceOf(ClientMessageValidator.Accepted.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/user/queue/history",
        "/user/queue/messages",
        "/user/queue/notification",
        "/topic/queue/event/4b0da4e4-7a7f-45f1-8bf9-575f6ba5a76c",
        "/topic/watchbox/4b0da4e4-7a7f-45f1-8bf9-575f6ba5a76c"
      })
  void appliesTheSupportedDestinationGrammarToUnsubscribe(String destination) {
    var validator = new ClientMessageValidator();
    var message =
        SocketMessage.newBuilder()
            .setType(SocketMessage.Type.UNSUBSCRIBE)
            .setDestination(destination)
            .build();

    assertThat(validator.validate(message)).isInstanceOf(ClientMessageValidator.Accepted.class);
  }

  @ParameterizedTest
  @MethodSource("destinationByteBoundCases")
  void boundsDestinationsByUtf8BytesBeforeReflectingThem(
      String destination, String expectedRejectedDestination) {
    var validator = new ClientMessageValidator();
    var message =
        SocketMessage.newBuilder()
            .setType(SocketMessage.Type.SUBSCRIBE)
            .setDestination(destination)
            .build();

    var rejection = rejection(validator.validate(message));

    assertThat(rejection).isNotNull();
    assertThat(rejection.errorCode())
        .isEqualTo(ClientMessageValidator.ErrorCode.INVALID_DESTINATION);
    assertThat(rejection.rejectedDestination()).isEqualTo(expectedRejectedDestination);
  }

  private static Stream<Arguments> destinationByteBoundCases() {
    String ascii128 = "/" + "a".repeat(127);
    String utf8Bytes128 = "é".repeat(64);
    String supplementaryUtf8Bytes128 = "😀".repeat(32);
    return Stream.of(
        Arguments.of(ascii128, ascii128),
        Arguments.of(ascii128 + "a", null),
        Arguments.of(utf8Bytes128, utf8Bytes128),
        Arguments.of(utf8Bytes128 + "a", null),
        Arguments.of(supplementaryUtf8Bytes128, supplementaryUtf8Bytes128),
        Arguments.of(supplementaryUtf8Bytes128 + "a", null));
  }

  @Test
  void rejectsAVeryLargeDestinationWithoutReflectingIt() {
    var message = control(SocketMessage.Type.SUBSCRIBE, "/" + "é".repeat(32_768)).build();

    var rejection = rejection(new ClientMessageValidator().validate(message));

    assertThat(rejection.errorCode())
        .isEqualTo(ClientMessageValidator.ErrorCode.INVALID_DESTINATION);
    assertThat(rejection.rejectedDestination()).isNull();
  }

  @ParameterizedTest
  @MethodSource("invalidKnownFieldCases")
  void rejectsNonDefaultUnusedKnownFieldsBeforeHandling(
      SocketMessage message, ClientMessageValidator.Operation operation) {
    var rejection = rejection(new ClientMessageValidator().validate(message));

    assertThat(rejection).isNotNull();
    assertThat(rejection.errorCode()).isEqualTo(ClientMessageValidator.ErrorCode.INVALID_FRAME);
    assertThat(rejection.operation()).isEqualTo(operation);
    assertThat(rejection.rejectedDestination()).isNull();
  }

  private static Stream<Arguments> invalidKnownFieldCases() {
    String destination = "/user/queue/history";
    return Stream.of(
        Arguments.of(
            control(SocketMessage.Type.SUBSCRIBE, destination).putHeaders("x", "value").build(),
            ClientMessageValidator.Operation.SUBSCRIBE),
        Arguments.of(
            control(SocketMessage.Type.SUBSCRIBE, destination)
                .setPayload(ByteString.copyFromUtf8("payload"))
                .build(),
            ClientMessageValidator.Operation.SUBSCRIBE),
        Arguments.of(
            control(SocketMessage.Type.SUBSCRIBE, destination).setCompressed(true).build(),
            ClientMessageValidator.Operation.SUBSCRIBE),
        Arguments.of(
            control(SocketMessage.Type.UNSUBSCRIBE, destination).putHeaders("x", "value").build(),
            ClientMessageValidator.Operation.UNSUBSCRIBE),
        Arguments.of(
            control(SocketMessage.Type.HEARTBEAT, "unexpected").build(),
            ClientMessageValidator.Operation.HEARTBEAT),
        Arguments.of(
            control(SocketMessage.Type.HEARTBEAT, "").putHeaders("x", "value").build(),
            ClientMessageValidator.Operation.HEARTBEAT),
        Arguments.of(
            control(SocketMessage.Type.HEARTBEAT, "")
                .setPayload(ByteString.copyFromUtf8("payload"))
                .build(),
            ClientMessageValidator.Operation.HEARTBEAT),
        Arguments.of(
            control(SocketMessage.Type.HEARTBEAT, "").setCompressed(true).build(),
            ClientMessageValidator.Operation.HEARTBEAT));
  }

  private static SocketMessage.Builder control(SocketMessage.Type type, String destination) {
    return SocketMessage.newBuilder().setType(type).setDestination(destination);
  }

  @ParameterizedTest
  @MethodSource("unsupportedTypeCases")
  void classifiesUnsupportedClientFrameTypesWithoutReflectingTheirContent(
      SocketMessage message,
      ClientMessageValidator.ErrorCode expectedCode,
      ClientMessageValidator.Operation expectedOperation) {
    var rejection = rejection(new ClientMessageValidator().validate(message));

    assertThat(rejection).isNotNull();
    assertThat(rejection.errorCode()).isEqualTo(expectedCode);
    assertThat(rejection.operation()).isEqualTo(expectedOperation);
    assertThat(rejection.rejectedDestination()).isNull();
  }

  private static Stream<Arguments> unsupportedTypeCases() {
    return Stream.of(
        unsupported(
            SocketMessage.Type.CONNECT,
            ClientMessageValidator.ErrorCode.UNSUPPORTED_FRAME_TYPE,
            ClientMessageValidator.Operation.CONNECT),
        unsupported(
            SocketMessage.Type.ERROR,
            ClientMessageValidator.ErrorCode.UNSUPPORTED_FRAME_TYPE,
            ClientMessageValidator.Operation.ERROR),
        unsupported(
            SocketMessage.Type.UNKNOWN,
            ClientMessageValidator.ErrorCode.UNSUPPORTED_FRAME_TYPE,
            ClientMessageValidator.Operation.UNKNOWN),
        Arguments.of(
            SocketMessage.newBuilder()
                .setTypeValue(999)
                .setDestination("must-not-be-reflected")
                .build(),
            ClientMessageValidator.ErrorCode.UNSUPPORTED_FRAME_TYPE,
            ClientMessageValidator.Operation.UNKNOWN),
        unsupported(
            SocketMessage.Type.SEND,
            ClientMessageValidator.ErrorCode.SEND_NOT_SUPPORTED,
            ClientMessageValidator.Operation.SEND));
  }

  private static Arguments unsupported(
      SocketMessage.Type type,
      ClientMessageValidator.ErrorCode code,
      ClientMessageValidator.Operation operation) {
    return Arguments.of(
        control(type, "must-not-be-reflected").putHeaders("submitted", "content").build(),
        code,
        operation);
  }

  @ParameterizedTest
  @MethodSource("invalidDestinationCases")
  void rejectsUnsupportedAndNoncanonicalDestinationGrammar(
      String destination, ClientMessageValidator.ErrorCode expectedCode) {
    var message = control(SocketMessage.Type.SUBSCRIBE, destination).build();

    var rejection = rejection(new ClientMessageValidator().validate(message));

    assertThat(rejection).isNotNull();
    assertThat(rejection.errorCode()).isEqualTo(expectedCode);
    assertThat(rejection.operation()).isEqualTo(ClientMessageValidator.Operation.SUBSCRIBE);
    assertThat(rejection.rejectedDestination()).isEqualTo(destination);
  }

  private static Stream<Arguments> invalidDestinationCases() {
    String id = "4b0da4e4-7a7f-45f1-8bf9-575f6ba5a76c";
    return Stream.of(
        Arguments.of("", ClientMessageValidator.ErrorCode.INVALID_DESTINATION),
        Arguments.of(
            "/user/queue/event/init", ClientMessageValidator.ErrorCode.INVALID_DESTINATION),
        Arguments.of("/user/queue/errors", ClientMessageValidator.ErrorCode.INVALID_DESTINATION),
        Arguments.of("/user/queue/history/", ClientMessageValidator.ErrorCode.INVALID_DESTINATION),
        Arguments.of(
            "/topic/queue/event/not-a-uuid", ClientMessageValidator.ErrorCode.INVALID_TOPIC),
        Arguments.of(
            "/topic/queue/event/" + id.toUpperCase(),
            ClientMessageValidator.ErrorCode.INVALID_TOPIC),
        Arguments.of(
            "/topic/queue/event/" + id + "/extra", ClientMessageValidator.ErrorCode.INVALID_TOPIC),
        Arguments.of(
            "/topic/queue/event/" + id + "?query=true",
            ClientMessageValidator.ErrorCode.INVALID_TOPIC),
        Arguments.of(
            "/topic/queue/event/" + id + "#fragment",
            ClientMessageValidator.ErrorCode.INVALID_TOPIC),
        Arguments.of("/topic/queue/event//" + id, ClientMessageValidator.ErrorCode.INVALID_TOPIC),
        Arguments.of("/topic/watchbox/not-a-uuid", ClientMessageValidator.ErrorCode.INVALID_TOPIC),
        Arguments.of(
            "/topic/watchbox/" + id.toUpperCase(), ClientMessageValidator.ErrorCode.INVALID_TOPIC),
        Arguments.of("/topic/mystery/" + id, ClientMessageValidator.ErrorCode.UNKNOWN_TOPIC),
        Arguments.of("/topic/x/queue/event/" + id, ClientMessageValidator.ErrorCode.UNKNOWN_TOPIC));
  }

  @Test
  void rejectsNullDestinationInputWithoutReflection() {
    var rejection =
        rejection(
            new ClientMessageValidator()
                .validateDestination(null, ClientMessageValidator.Operation.SUBSCRIBE));

    assertThat(rejection).isNotNull();
    assertThat(rejection.errorCode())
        .isEqualTo(ClientMessageValidator.ErrorCode.INVALID_DESTINATION);
    assertThat(rejection.rejectedDestination()).isNull();
  }

  @Test
  void ignoresUnknownProtobufFieldsAfterKnownFieldsValidate() {
    var unknownFields =
        UnknownFieldSet.newBuilder()
            .addField(999, UnknownFieldSet.Field.newBuilder().addVarint(42).build())
            .build();
    var message =
        control(SocketMessage.Type.SUBSCRIBE, "/user/queue/history")
            .setUnknownFields(unknownFields)
            .build();

    assertThat(new ClientMessageValidator().validate(message))
        .isInstanceOf(ClientMessageValidator.Accepted.class);
  }

  private static ClientMessageValidator.Rejection rejection(
      ClientMessageValidator.Validation validation) {
    assertThat(validation).isInstanceOf(ClientMessageValidator.Rejected.class);
    return ((ClientMessageValidator.Rejected) validation).rejection();
  }
}
