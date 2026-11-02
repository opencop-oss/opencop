// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.export;

import com.opencop.core.api.general.technical.model.export.ExportRequest;
import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public record ExportSelection(Mode mode, List<SubjectSelection> subjects, List<UUID> elementIds) {

  public static final int MAX_SELECTIONS = 10_000;
  private static final String SOURCE_TRANSITIVE_MESSAGE =
      "Data sources do not have transitive data";

  public static ExportSelection from(ExportRequest request) {
    if (request == null) {
      throwBadRequest("Export request must not be null");
    }

    var subjectCollectionPresent = request.subjects() != null;
    var elementCollectionPresent = request.elementIds() != null;
    if (subjectCollectionPresent && elementCollectionPresent) {
      throwBadRequest("Export request must include either subjects or elementIds, not both");
    }
    if (!subjectCollectionPresent && !elementCollectionPresent) {
      throwBadRequest("Export request must include subjects or elementIds");
    }

    if (subjectCollectionPresent) {
      return fromSubjects(request.subjects());
    }
    return fromElementIds(request.elementIds());
  }

  private static ExportSelection fromSubjects(List<ExportRequest.Subject> requestSubjects) {
    if (requestSubjects.isEmpty()) {
      throwBadRequest("Export request subjects must not be empty");
    }
    if (requestSubjects.size() > MAX_SELECTIONS) {
      throwBadRequest("Export request subjects cannot exceed " + MAX_SELECTIONS + " entries");
    }

    var uniqueSubjects = new HashSet<SubjectKey>();
    var subjects =
        requestSubjects.stream()
            .map(ExportSelection::toSubjectSelection)
            .peek(ExportSelection::validateSourceTransitiveData)
            .peek(subject -> validateUniqueSubject(uniqueSubjects, subject))
            .toList();
    return new ExportSelection(Mode.SUBJECTS, List.copyOf(subjects), List.of());
  }

  private static ExportSelection fromElementIds(List<UUID> requestElementIds) {
    if (requestElementIds.isEmpty()) {
      throwBadRequest("Export request elementIds must not be empty");
    }
    if (requestElementIds.size() > MAX_SELECTIONS) {
      throwBadRequest("Export request elementIds cannot exceed " + MAX_SELECTIONS + " entries");
    }

    var elementIds = new HashSet<UUID>();
    requestElementIds.forEach(elementId -> validateUniqueElementId(elementIds, elementId));
    return new ExportSelection(Mode.ELEMENT_IDS, List.of(), List.copyOf(requestElementIds));
  }

  private static SubjectSelection toSubjectSelection(ExportRequest.Subject subject) {
    if (subject == null) {
      throwBadRequest("Export request subjects must not contain null entries");
    }
    if (subject.subjectType() == null) {
      throwBadRequest("Export request subject is missing subjectType");
    }
    if (subject.subjectId() == null) {
      throwBadRequest("Export request subject is missing subjectId");
    }
    return new SubjectSelection(
        subject.subjectType(), subject.subjectId(), subject.includeTransitiveDataOrDefault());
  }

  private static void validateSourceTransitiveData(SubjectSelection subject) {
    if (subject.subjectType() == SubjectType.SOURCE && subject.includeTransitiveData()) {
      throwBadRequest(SOURCE_TRANSITIVE_MESSAGE);
    }
  }

  private static void validateUniqueSubject(
      HashSet<SubjectKey> uniqueSubjects, SubjectSelection subject) {
    if (!uniqueSubjects.add(new SubjectKey(subject.subjectType(), subject.subjectId()))) {
      throwBadRequest(
          "Export request subjects must not contain duplicate (subjectType, subjectId) pairs");
    }
  }

  private static void validateUniqueElementId(HashSet<UUID> elementIds, UUID elementId) {
    if (elementId == null) {
      throwBadRequest("Export request elementIds must not contain null entries");
    }
    if (!elementIds.add(elementId)) {
      throwBadRequest("Export request elementIds must not contain duplicates");
    }
  }

  private static void throwBadRequest(String message) {
    throw new GlobalOpenCopException(message, HttpStatus.BAD_REQUEST);
  }

  public enum Mode {
    SUBJECTS,
    ELEMENT_IDS
  }

  public record SubjectSelection(
      SubjectType subjectType, UUID subjectId, boolean includeTransitiveData) {}

  private record SubjectKey(SubjectType subjectType, UUID subjectId) {}
}
