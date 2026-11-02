// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.technical.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.model.watchbox.WatchboxAlertingStatus;
import com.opencop.core.sdk.model.watchbox.WatchboxCapabilities;
import com.opencop.core.sdk.model.watchbox.WatchboxCreateRequest;
import com.opencop.core.sdk.model.watchbox.WatchboxErrorResponse;
import com.opencop.core.sdk.model.watchbox.WatchboxFilterEvaluator;
import com.opencop.core.sdk.model.watchbox.WatchboxFilterMatch;
import com.opencop.core.sdk.model.watchbox.WatchboxFilterTestRequest;
import com.opencop.core.sdk.model.watchbox.WatchboxPatchRequest;
import com.opencop.core.sdk.model.watchbox.WatchboxResponse;
import com.opencop.core.sdk.model.watchbox.WatchboxStatusOverrideRequest;
import com.opencop.core.sdk.model.watchbox.WatchboxStatusReason;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class WatchboxOpenApiExamplesTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void allOpenApiExampleConstantsAreValidJson() throws Exception {
    for (var field : WatchboxOpenApiExamples.class.getDeclaredFields()) {
      if (field.getType() == String.class && Modifier.isStatic(field.getModifiers())) {
        assertThat(objectMapper.readValue((String) field.get(null), Object.class))
            .as(field.getName())
            .isNotNull();
      }
    }
  }

  @Test
  void selectedOpenApiExamplesMatchTheirDtoShapes() {
    var page =
        objectMapper.readValue(
            WatchboxOpenApiExamples.DETECTION_PAGE,
            new tools.jackson.core.type.TypeReference<
                com.opencop.core.sdk.technical.gateway.db.PageResponse<
                    WatchboxDetectionResponse>>() {});
    assertThat(page.content()).hasSize(1);
    var detail = objectMapper.readTree(WatchboxOpenApiExamples.DETECTION_DETAIL);
    assertThat(detail.path("triggeringEvent").isNull()).isTrue();
    assertThat(detail.path("latestEvent").hasNonNull("geometry")).isTrue();
    assertThat(
            objectMapper.readValue(
                WatchboxOpenApiExamples.CREATE_REQUEST, WatchboxCreateRequest.class))
        .isNotNull();
    assertThat(
            objectMapper.readValue(
                WatchboxOpenApiExamples.PATCH_RESET_REQUEST, WatchboxPatchRequest.class))
        .isNotNull();
    assertThat(
            objectMapper.readValue(
                WatchboxOpenApiExamples.DEV_OVERRIDE_REQUEST, WatchboxStatusOverrideRequest.class))
        .isNotNull();
    assertThat(
            objectMapper.readValue(
                WatchboxOpenApiExamples.WATCHBOX_CREATE_SUCCESS, WatchboxResponse.class))
        .isNotNull();
    assertThat(
            objectMapper.readValue(
                WatchboxOpenApiExamples.WATCHBOX_DETAIL_WITH_SOURCE_STATUS, WatchboxResponse.class))
        .isNotNull();
    assertThat(
            objectMapper.readValue(
                WatchboxOpenApiExamples.DEV_OVERRIDE_RESPONSE, WatchboxResponse.class))
        .isNotNull();
    assertThat(
            objectMapper.readValue(
                WatchboxOpenApiExamples.FILTER_TEST_REQUEST, WatchboxFilterTestRequest.class))
        .isNotNull();
    assertThat(
            objectMapper.readValue(
                WatchboxOpenApiExamples.FILTER_TEST_REQUEST_NOT_FRIENDLY,
                WatchboxFilterTestRequest.class))
        .isNotNull();
    assertThat(
            objectMapper.readValue(
                WatchboxOpenApiExamples.FILTER_TEST_RESPONSE_MATCH, WatchboxFilterMatch.class))
        .isNotNull();
    assertThat(
            objectMapper.readValue(
                WatchboxOpenApiExamples.FILTER_TEST_RESPONSE_NOT_FRIENDLY_MATCH,
                WatchboxFilterMatch.class))
        .isNotNull();
    assertThat(
            objectMapper.readValue(
                WatchboxOpenApiExamples.TRACKING_RESET_REQUIRED, WatchboxErrorResponse.class))
        .isNotNull();
    assertThat(
            objectMapper.readValue(
                WatchboxOpenApiExamples.INVALID_REQUEST_MALFORMED, WatchboxErrorResponse.class))
        .isNotNull();
    assertThat(
            objectMapper.readValue(WatchboxOpenApiExamples.WATCHBOX_LIST, WatchboxResponse[].class))
        .isNotNull();
  }

  /**
   * The isNotNull round-trips above tolerate a missing record component (it deserializes to null),
   * so the capabilities field is pinned by value in every response example.
   */
  @Test
  void responseExamples_whenParsed_carryCallerCapabilities() {
    assertThat(
            objectMapper
                .readValue(WatchboxOpenApiExamples.WATCHBOX_CREATE_SUCCESS, WatchboxResponse.class)
                .capabilities())
        .isEqualTo(new WatchboxCapabilities(true, true));
    assertThat(
            objectMapper
                .readValue(
                    WatchboxOpenApiExamples.WATCHBOX_DETAIL_WITH_SOURCE_STATUS,
                    WatchboxResponse.class)
                .capabilities())
        .isEqualTo(new WatchboxCapabilities(true, true));
    assertThat(
            objectMapper
                .readValue(WatchboxOpenApiExamples.DEV_OVERRIDE_RESPONSE, WatchboxResponse.class)
                .capabilities())
        .isEqualTo(new WatchboxCapabilities(true, true));

    // The list example deliberately shows both sides of the decision: a fully visible
    // non-manager row alongside manager rows.
    var list =
        objectMapper.readValue(WatchboxOpenApiExamples.WATCHBOX_LIST, WatchboxResponse[].class);
    assertThat(list)
        .extracting(WatchboxResponse::capabilities)
        .doesNotContainNull()
        .contains(new WatchboxCapabilities(false, false), new WatchboxCapabilities(true, true));
  }

  @Test
  void openApiShadowRequests_whenComparedToSdkRequests_matchComponents() {
    assertThat(recordComponents(WatchboxOpenApiSchemas.CreateRequest.class))
        .containsExactlyElementsOf(recordComponents(WatchboxCreateRequest.class));
    assertThat(recordComponents(WatchboxOpenApiSchemas.PatchRequest.class))
        .containsExactlyElementsOf(recordComponents(WatchboxPatchRequest.class));
  }

  @Test
  void filterTestResponseExamplesMatchEvaluatorOutput() {
    assertResponseExampleMatchesEvaluator(
        WatchboxOpenApiExamples.FILTER_TEST_REQUEST,
        WatchboxOpenApiExamples.FILTER_TEST_RESPONSE_MATCH);
    assertResponseExampleMatchesEvaluator(
        WatchboxOpenApiExamples.FILTER_TEST_REQUEST_NOT_FRIENDLY,
        WatchboxOpenApiExamples.FILTER_TEST_RESPONSE_NOT_FRIENDLY_MATCH);
  }

  @Test
  void responseExamples_whenParsed_excludeRetiredVisibilityAndAudienceFields() {
    assertRetiredResponseFieldsAbsent(WatchboxOpenApiExamples.WATCHBOX_CREATE_SUCCESS);
    assertRetiredResponseFieldsAbsent(WatchboxOpenApiExamples.WATCHBOX_DETAIL_WITH_SOURCE_STATUS);
    assertRetiredResponseFieldsAbsent(WatchboxOpenApiExamples.DEV_OVERRIDE_RESPONSE);

    List<?> list = objectMapper.readValue(WatchboxOpenApiExamples.WATCHBOX_LIST, List.class);
    assertThat(list).allSatisfy(item -> assertRetiredResponseFieldsAbsent(asMap(item)));
    assertThat(asMap(list.get(0))).containsEntry("sourceStatuses", List.of());
  }

  @Test
  void watchboxList_churnExampleMatchesPersistedRuntimeSemantics() {
    var responses =
        objectMapper.readValue(WatchboxOpenApiExamples.WATCHBOX_LIST, WatchboxResponse[].class);

    assertThat(responses).hasSize(3);
    var churn = responses[2];
    assertThat(churn.alertingStatus()).isEqualTo(WatchboxAlertingStatus.DEGRADED);
    assertThat(churn.alertingReasons())
        .containsExactly(WatchboxStatusReason.WATCHBOX_OBJECT_ID_CHURN_DETECTED);
    assertThat(churn.sourceStatuses())
        .singleElement()
        .satisfies(
            status -> {
              assertThat(status.status()).isEqualTo(WatchboxAlertingStatus.DEGRADED);
              assertThat(status.reason())
                  .isEqualTo(WatchboxStatusReason.WATCHBOX_OBJECT_ID_CHURN_DETECTED);
              assertThat(status.observedCount()).isEqualTo(10_001L);
              assertThat(status.cap()).isEqualTo(10_000L);
            });
  }

  private void assertRetiredResponseFieldsAbsent(String example) {
    assertRetiredResponseFieldsAbsent(objectMapper.readValue(example, Map.class));
  }

  private static void assertRetiredResponseFieldsAbsent(Map<String, Object> response) {
    assertThat(response)
        .doesNotContainKeys(
            "hasOmittedLayerIds",
            "effectiveRecipientCount",
            "effectiveRecipientCountDisplay",
            "recipientCap");
  }

  private void assertResponseExampleMatchesEvaluator(
      String requestExample, String responseExample) {
    WatchboxFilterTestRequest request =
        objectMapper.readValue(requestExample, WatchboxFilterTestRequest.class);
    WatchboxFilterMatch expected =
        objectMapper.readValue(responseExample, WatchboxFilterMatch.class);

    assertThat(WatchboxFilterEvaluator.evaluate(request.filter(), request.feature()))
        .isEqualTo(expected);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value) {
    return (Map<String, Object>) value;
  }

  private static List<RecordComponent> recordComponents(Class<?> recordType) {
    return Arrays.stream(recordType.getRecordComponents())
        .map(component -> new RecordComponent(component.getName(), component.getType()))
        .toList();
  }

  private record RecordComponent(String name, Class<?> type) {}
}
