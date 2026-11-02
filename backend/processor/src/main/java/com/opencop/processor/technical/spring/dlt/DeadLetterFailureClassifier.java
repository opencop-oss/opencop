// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.dlt;

import com.opencop.core.sdk.ports.gateway.blob.CloudStorageException;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.InvalidMappingDiagnosticException;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.MappingInputException;
import com.opencop.core.sdk.ports.gateway.repository.RepositoryException;
import com.opencop.core.sdk.ports.gateway.repository.RepositoryExceptionCodes;
import com.opencop.core.sdk.technical.gateway.mapping.ingress.RawDataParsingException;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import com.opencop.processor.ports.business.usecase.event.MissingHeaderException;
import java.sql.SQLException;
import org.apache.kafka.common.KafkaException;
import tools.jackson.core.JacksonException;

final class DeadLetterFailureClassifier {

  private DeadLetterFailureClassifier() {}

  static DeadLetterHeaders.FailureKind classify(Throwable failure) {
    var root = rootCause(failure);
    if (isUnsupported(root)) {
      return DeadLetterHeaders.FailureKind.CONTROL_UNSUPPORTED;
    }
    var inputFailure = causeOfType(failure, MappingInputException.class);
    if (inputFailure != null) {
      return switch (inputFailure.reason()) {
        case MAPPER_CONFIGURATION -> DeadLetterHeaders.FailureKind.INFRA;
        case UNKNOWN -> DeadLetterHeaders.FailureKind.PROCESSING;
        case MALFORMED_PAYLOAD,
            INPUT_TOO_LARGE,
            UNSAFE_INPUT,
            STRUCTURAL_LIMIT_EXCEEDED,
            INVALID_ROOT ->
            DeadLetterHeaders.FailureKind.MALFORMED;
      };
    }
    if (isMalformed(root)) {
      return DeadLetterHeaders.FailureKind.MALFORMED;
    }
    if (hasCause(failure, InvalidMappingDiagnosticException.class)
        || hasCause(failure, MapperEngineException.class)
        || hasCause(failure, RawDataParsingException.class)) {
      return DeadLetterHeaders.FailureKind.MAPPING_ERROR;
    }
    if (isInfrastructure(root)) {
      return DeadLetterHeaders.FailureKind.INFRA;
    }
    return DeadLetterHeaders.FailureKind.PROCESSING;
  }

  static Throwable rootCause(Throwable failure) {
    var root = failure;
    while (root.getCause() != null) {
      root = root.getCause();
    }
    return root;
  }

  private static boolean isUnsupported(Throwable failure) {
    if (failure instanceof UnsupportedOperationException) {
      return true;
    }
    var message = failure.getMessage();
    return message != null && message.toLowerCase().contains("unsupported");
  }

  private static boolean isMalformed(Throwable failure) {
    if (failure instanceof MissingHeaderException
        || failure instanceof IllegalArgumentException
        || failure instanceof JacksonException) {
      return true;
    }
    if (failure instanceof RepositoryException repositoryException) {
      return RepositoryExceptionCodes.BAD_REQUEST.code().equals(repositoryException.getCode());
    }
    return false;
  }

  private static boolean isInfrastructure(Throwable failure) {
    if (failure instanceof RepositoryException repositoryException) {
      return !RepositoryExceptionCodes.BAD_REQUEST.code().equals(repositoryException.getCode());
    }
    return failure instanceof CloudStorageException
        || failure instanceof KafkaException
        || failure instanceof SQLException
        || failure instanceof java.io.IOException;
  }

  private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
    return causeOfType(failure, type) != null;
  }

  private static <T extends Throwable> T causeOfType(Throwable failure, Class<T> type) {
    var current = failure;
    while (current != null) {
      if (type.isInstance(current)) {
        return type.cast(current);
      }
      current = current.getCause();
    }
    return null;
  }
}
