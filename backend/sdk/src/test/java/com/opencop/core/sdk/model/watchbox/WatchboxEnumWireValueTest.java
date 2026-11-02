// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.watchbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class WatchboxEnumWireValueTest {

  private static final Pattern STABLE_CODE = Pattern.compile("WATCHBOX_[A-Z0-9_]+");
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void alertingStatusWireValuesRemainStable() {
    assertThat(enumNames(WatchboxAlertingStatus.class))
        .containsExactlyInAnyOrder("ACTIVE", "DEGRADED", "FAILED", "DISABLED");
  }

  @Test
  void statusReasonWireValuesRemainStableAndBounded() {
    assertThat(enumNames(WatchboxStatusReason.class))
        .containsExactlyInAnyOrder(
            "WATCHBOX_DISABLED",
            "WATCHBOX_MAPPER_OBJECT_ID_NOT_CONFIGURED",
            "WATCHBOX_SOURCE_OBJECT_ID_UNSTABLE_OR_UNKNOWN",
            "WATCHBOX_OBJECT_ID_CHURN_DETECTED",
            "WATCHBOX_DETECTION_NOT_RUNNING",
            "WATCHBOX_DETECTION_PARTIALLY_STALLED");
  }

  @Test
  void errorCodeWireValuesRemainStableAndBounded() {
    assertThat(enumNames(WatchboxErrorCode.class))
        .containsExactlyInAnyOrder(
            "WATCHBOX_INVALID_REQUEST",
            "WATCHBOX_INVALID_POLYGON",
            "WATCHBOX_INVALID_FILTER",
            "WATCHBOX_INVALID_NAME",
            "WATCHBOX_INVALID_DESCRIPTION",
            "WATCHBOX_LAYER_REQUIRED",
            "WATCHBOX_LAYER_LIMIT_EXCEEDED",
            "WATCHBOX_FILTER_LIMIT_EXCEEDED",
            "WATCHBOX_MANAGEMENT_ROLE_REQUIRED",
            "WATCHBOX_NO_ELIGIBLE_SOURCES",
            "WATCHBOX_SOURCE_ELIGIBILITY_FAILED",
            "WATCHBOX_MAPPER_UNAVAILABLE",
            "WATCHBOX_MAPPER_OBJECT_ID_NOT_CONFIGURED",
            "WATCHBOX_OBJECT_ID_ON_MISSING_NOT_ERROR",
            "WATCHBOX_SOURCE_OBJECT_ID_UNSTABLE_OR_UNKNOWN",
            "WATCHBOX_TRACKING_RESET_CONFIRMATION_REQUIRED",
            "WATCHBOX_FEATURE_DISABLED",
            "WATCHBOX_NOT_FOUND");
  }

  @Test
  void resetReasonWireValuesRemainStable() {
    assertThat(enumNames(WatchboxResetReason.class))
        .containsExactlyInAnyOrder("POLYGON_CHANGED", "LAYER_REMOVED", "REENABLED");
  }

  @Test
  void directionWireValuesRemainStable() {
    assertThat(enumNames(WatchboxDirection.class))
        .containsExactlyInAnyOrder("ENTER", "LEAVE", "BOTH");
  }

  @Test
  void filterOperationWireValuesRemainStable() {
    assertThat(enumNames(WatchboxFilterOperation.class))
        .containsExactlyInAnyOrder("EXISTS", "EQUALS", "IN", "SUBSTRING_IN");
  }

  @Test
  void reasonCodesDoNotCarryRawIdsCoordinatesOrMessages() {
    assertThat(enumNames(WatchboxStatusReason.class)).allMatch(WatchboxEnumWireValueTest::isStable);
    assertThat(enumNames(WatchboxErrorCode.class)).allMatch(WatchboxEnumWireValueTest::isStable);
  }

  @Test
  void enumJsonWireValueIsTheConstantName() {
    assertJsonRoundTrip(WatchboxAlertingStatus.class);
    assertJsonRoundTrip(WatchboxStatusReason.class);
    assertJsonRoundTrip(WatchboxErrorCode.class);
    assertJsonRoundTrip(WatchboxResetReason.class);
    assertJsonRoundTrip(WatchboxDirection.class);
    assertJsonRoundTrip(WatchboxFilterOperation.class);
  }

  @Test
  void errorResponseCodeUsesTypedEnumAndStringJsonWireValue() {
    var response =
        new WatchboxErrorResponse(
            WatchboxErrorCode.WATCHBOX_FEATURE_DISABLED,
            "Watchbox is disabled",
            java.util.List.of(),
            java.util.List.of());

    assertThat(response.code()).isEqualTo(WatchboxErrorCode.WATCHBOX_FEATURE_DISABLED);
    String json = objectMapper.writeValueAsString(response);
    assertThat(json).contains("\"code\":\"WATCHBOX_FEATURE_DISABLED\"");
    assertThat(objectMapper.readValue(json, WatchboxErrorResponse.class).code())
        .isEqualTo(WatchboxErrorCode.WATCHBOX_FEATURE_DISABLED);
  }

  @Test
  void errorResponseContextIsDefensivelyCopiedAndRoundTrips() {
    var names = new ArrayList<>(List.of("Source A"));
    var context = new LinkedHashMap<String, List<String>>();
    context.put("sourceNames", names);
    var response =
        new WatchboxErrorResponse(
            WatchboxErrorCode.WATCHBOX_SOURCE_ELIGIBILITY_FAILED,
            "Selected sources require object ID mapping correction",
            List.of("layerIds"),
            List.of(),
            context);

    names.add("Source B");
    context.put("other", List.of("ignored"));

    assertThat(response.context()).containsExactly(Map.entry("sourceNames", List.of("Source A")));
    assertThatThrownBy(() -> response.context().get("sourceNames").add("Source C"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(new WatchboxErrorResponse(null, null, null, null, null).context()).isEmpty();

    String json = objectMapper.writeValueAsString(response);
    var decoded = objectMapper.readValue(json, WatchboxErrorResponse.class);
    assertThat(decoded.code()).isEqualTo(WatchboxErrorCode.WATCHBOX_SOURCE_ELIGIBILITY_FAILED);
    assertThat(decoded.context()).containsEntry("sourceNames", List.of("Source A"));
  }

  private <T extends Enum<T>> void assertJsonRoundTrip(Class<T> enumType) {
    for (T value : enumType.getEnumConstants()) {
      String json = objectMapper.writeValueAsString(value);
      assertThat(json).isEqualTo("\"" + value.name() + "\"");
      assertThat(objectMapper.readValue(json, enumType)).isEqualTo(value);
    }
  }

  private static Set<String> enumNames(Class<? extends Enum<?>> enumType) {
    return Arrays.stream(enumType.getEnumConstants()).map(Enum::name).collect(Collectors.toSet());
  }

  private static boolean isStable(String reason) {
    return STABLE_CODE.matcher(reason).matches()
        && !reason.matches(".*[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,}.*")
        && !reason.contains(".")
        && !reason.contains(",")
        && !reason.contains(":");
  }
}
