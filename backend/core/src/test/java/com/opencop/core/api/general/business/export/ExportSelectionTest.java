// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.opencop.core.api.general.technical.model.export.ExportRequest;
import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ExportSelectionTest {

  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655441001");
  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655441003");
  private static final UUID ELEMENT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655441004");
  private static final UUID ELEMENT_ID_TWO =
      UUID.fromString("550e8400-e29b-41d4-a716-446655441005");
  private static final int MAX_SELECTION_BUDGET = 10_000;
  private static final int FIRST_REJECTED_SELECTION_COUNT = MAX_SELECTION_BUDGET + 1;

  @Test
  void from_whenSingleSubjectProvided_returnsSingleSubjectSelection() {
    var request =
        new ExportRequest(
            ExportRequest.Format.CSV, List.of(subjectRequest(SubjectType.LAYER, LAYER_ID)), null);

    var selection = ExportSelection.from(request);

    assertEquals(ExportSelection.Mode.SUBJECTS, selection.mode());
    assertEquals(List.of(subject(SubjectType.LAYER, LAYER_ID, false)), selection.subjects());
    assertEquals(List.of(), selection.elementIds());
  }

  @Test
  void from_whenMultipleSubjectsProvided_preservesRequestOrder() {
    var request =
        new ExportRequest(
            ExportRequest.Format.CSV,
            List.of(
                subjectRequest(SubjectType.LAYER, LAYER_ID),
                subjectRequest(SubjectType.SOURCE, SOURCE_ID)),
            null);

    var selection = ExportSelection.from(request);

    assertEquals(ExportSelection.Mode.SUBJECTS, selection.mode());
    assertEquals(
        List.of(
            subject(SubjectType.LAYER, LAYER_ID, false),
            subject(SubjectType.SOURCE, SOURCE_ID, false)),
        selection.subjects());
  }

  @Test
  void from_whenSubjectTypesShareId_preservesBothSubjectsInRequestOrder() {
    var request =
        new ExportRequest(
            ExportRequest.Format.CSV,
            List.of(
                subjectRequest(SubjectType.LAYER, LAYER_ID),
                subjectRequest(SubjectType.SOURCE, LAYER_ID)),
            null);

    var selection = ExportSelection.from(request);

    assertEquals(ExportSelection.Mode.SUBJECTS, selection.mode());
    assertEquals(
        List.of(
            subject(SubjectType.LAYER, LAYER_ID, false),
            subject(SubjectType.SOURCE, LAYER_ID, false)),
        selection.subjects());
  }

  @Test
  void from_whenElementIdsProvided_preservesRequestOrder() {
    var request =
        new ExportRequest(ExportRequest.Format.CSV, null, List.of(ELEMENT_ID, ELEMENT_ID_TWO));

    var selection = ExportSelection.from(request);

    assertEquals(ExportSelection.Mode.ELEMENT_IDS, selection.mode());
    assertEquals(List.of(ELEMENT_ID, ELEMENT_ID_TWO), selection.elementIds());
    assertEquals(List.of(), selection.subjects());
  }

  @Test
  void from_whenNeitherSubjectsNorElementIdsProvided_returnsBadRequestNamingBothFields() {
    assertBadRequestWithMessage(
        new ExportRequest(ExportRequest.Format.CSV, null, null),
        "Export request must include subjects or elementIds");
  }

  @Test
  void from_whenSubjectsListIsEmpty_returnsBadRequestNamingSubjects() {
    assertBadRequestWithMessage(
        new ExportRequest(ExportRequest.Format.CSV, List.of(), null),
        "Export request subjects must not be empty");
  }

  @Test
  void from_whenElementIdsListIsEmpty_returnsBadRequestNamingElementIds() {
    assertBadRequestWithMessage(
        new ExportRequest(ExportRequest.Format.CSV, null, List.of()),
        "Export request elementIds must not be empty");
  }

  @Test
  void from_whenBothSubjectsAndElementIdsProvided_returnsBadRequestNamingBothFields() {
    assertBadRequestWithMessage(
        new ExportRequest(
            ExportRequest.Format.CSV,
            List.of(subjectRequest(SubjectType.LAYER, LAYER_ID)),
            List.of(ELEMENT_ID)),
        "Export request must include either subjects or elementIds, not both");
  }

  @Test
  void from_whenSubjectsContainDuplicate_returnsBadRequestNamingDuplicatePair() {
    assertBadRequestWithMessage(
        new ExportRequest(
            ExportRequest.Format.CSV,
            List.of(
                subjectRequest(SubjectType.LAYER, LAYER_ID),
                subjectRequest(SubjectType.LAYER, LAYER_ID)),
            null),
        "Export request subjects must not contain duplicate (subjectType, subjectId) pairs");
  }

  @Test
  void from_whenElementIdsContainDuplicate_returnsBadRequestNamingDuplicateElement() {
    assertBadRequestWithMessage(
        new ExportRequest(ExportRequest.Format.CSV, null, List.of(ELEMENT_ID, ELEMENT_ID)),
        "Export request elementIds must not contain duplicates");
  }

  @Test
  void from_whenSubjectsContainNullEntry_returnsBadRequestNamingNullSubject() {
    assertBadRequestWithMessage(
        new ExportRequest(ExportRequest.Format.CSV, listWithNullSubject(), null),
        "Export request subjects must not contain null entries");
  }

  @Test
  void from_whenSubjectMissingSubjectType_returnsBadRequestNamingSubjectType() {
    assertBadRequestWithMessage(
        new ExportRequest(
            ExportRequest.Format.CSV,
            List.of(new ExportRequest.Subject(null, LAYER_ID, null)),
            null),
        "Export request subject is missing subjectType");
  }

  @Test
  void from_whenSubjectMissingSubjectId_returnsBadRequestNamingSubjectId() {
    assertBadRequestWithMessage(
        new ExportRequest(
            ExportRequest.Format.CSV,
            List.of(new ExportRequest.Subject(SubjectType.LAYER, null, null)),
            null),
        "Export request subject is missing subjectId");
  }

  @Test
  void from_whenElementIdsContainNullEntry_returnsBadRequestNamingNullElement() {
    assertBadRequestWithMessage(
        new ExportRequest(ExportRequest.Format.CSV, null, listWithNullElement()),
        "Export request elementIds must not contain null entries");
  }

  @Test
  void from_whenSourceRequestsTransitiveData_returnsBadRequest() {
    assertBadRequestWithMessage(
        new ExportRequest(
            ExportRequest.Format.CSV,
            List.of(new ExportRequest.Subject(SubjectType.SOURCE, SOURCE_ID, true)),
            null),
        "Data sources do not have transitive data");
  }

  @Test
  void from_whenSubjectsExceedMaximum_returnsBadRequestNamingSubjectsLimit() {
    assertBadRequestWithMessage(
        new ExportRequest(ExportRequest.Format.CSV, tooManySubjects(), null),
        "Export request subjects cannot exceed 10000 entries");
  }

  @Test
  void from_whenElementIdsExceedMaximum_returnsBadRequestNamingElementIdsLimit() {
    assertBadRequestWithMessage(
        new ExportRequest(ExportRequest.Format.CSV, null, tooManyElementIds()),
        "Export request elementIds cannot exceed 10000 entries");
  }

  @Test
  void from_whenSelectionMatchesMaximumBudget_acceptsRequest() {
    var subjectRequest = new ExportRequest(ExportRequest.Format.CSV, maximumSubjectBudget(), null);
    var elementRequest = new ExportRequest(ExportRequest.Format.CSV, null, maximumElementBudget());

    assertEquals(MAX_SELECTION_BUDGET, ExportSelection.from(subjectRequest).subjects().size());
    assertEquals(MAX_SELECTION_BUDGET, ExportSelection.from(elementRequest).elementIds().size());
  }

  private static ExportSelection.SubjectSelection subject(
      SubjectType subjectType, UUID subjectId, boolean includeTransitiveData) {
    return new ExportSelection.SubjectSelection(subjectType, subjectId, includeTransitiveData);
  }

  private static ExportRequest.Subject subjectRequest(SubjectType subjectType, UUID subjectId) {
    return new ExportRequest.Subject(subjectType, subjectId, null);
  }

  private static List<ExportRequest.Subject> listWithNullSubject() {
    var subjects = new ArrayList<ExportRequest.Subject>();
    subjects.add(null);
    return subjects;
  }

  private static List<UUID> listWithNullElement() {
    var elementIds = new ArrayList<UUID>();
    elementIds.add(null);
    return elementIds;
  }

  private static List<ExportRequest.Subject> tooManySubjects() {
    return IntStream.range(0, FIRST_REJECTED_SELECTION_COUNT)
        .mapToObj(index -> subjectRequest(SubjectType.LAYER, uuidFromIndex(index)))
        .toList();
  }

  private static List<UUID> tooManyElementIds() {
    return IntStream.range(0, FIRST_REJECTED_SELECTION_COUNT)
        .mapToObj(ExportSelectionTest::uuidFromIndex)
        .toList();
  }

  private static List<ExportRequest.Subject> maximumSubjectBudget() {
    return IntStream.range(0, MAX_SELECTION_BUDGET)
        .mapToObj(index -> subjectRequest(SubjectType.LAYER, uuidFromIndex(index)))
        .toList();
  }

  private static List<UUID> maximumElementBudget() {
    return IntStream.range(0, MAX_SELECTION_BUDGET)
        .mapToObj(ExportSelectionTest::uuidFromIndex)
        .toList();
  }

  private static UUID uuidFromIndex(int index) {
    return UUID.fromString("550e8400-e29b-41d4-a716-%012d".formatted(index));
  }

  private static void assertBadRequestWithMessage(ExportRequest request, String expectedMessage) {
    var exception = assertThrows(GlobalOpenCopException.class, () -> ExportSelection.from(request));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    assertEquals(expectedMessage, exception.getMessage());
  }
}
