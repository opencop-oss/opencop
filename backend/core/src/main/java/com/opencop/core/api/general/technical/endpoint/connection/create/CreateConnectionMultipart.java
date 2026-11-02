// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.connection.create;

import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.api.general.technical.model.connectioninput.MultipartConnectionInputResourceAdapter;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputResource;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

/**
 * Splits a multipart creation request into its JSON and its files. The JSON travels as the one part
 * named {@code body}, as a text part or a file; every other part is a file for the extractor field
 * it is named after, and a name repeated across parts supplies a list.
 *
 * <p>Only the request's consistency with itself is checked here: a value belongs in the JSON, so a
 * text part under any other name is rejected rather than ignored. Whether a file is acceptable is
 * the extractor's to decide.
 */
final class CreateConnectionMultipart {

  static final String BODY_PART = "body";

  private CreateConnectionMultipart() {}

  /** The request's JSON, once exactly one part carries it. */
  static String body(MultipartHttpServletRequest request) {
    var texts = textParts(request);
    var unexpected = texts.stream().filter(part -> !BODY_PART.equals(part.getName())).findFirst();
    if (unexpected.isPresent()) {
      throw unexpectedTextPart(unexpected.get().getName());
    }
    var files = request.getMultiFileMap().getOrDefault(BODY_PART, List.of());
    var parts = texts.size() + files.size();
    if (parts == 0) {
      throw new ConnectionManagementException(ConnectionExceptionCodes.INVALID_REQUEST_BODY);
    }
    if (parts > 1) {
      throw ConnectionManagementException.forField(
          ConnectionExceptionCodes.DUPLICATE_REQUEST_BODY, BODY_PART);
    }
    return texts.isEmpty() ? text(files.getFirst()) : text(texts.getFirst());
  }

  /** Every file part other than the JSON, as a resource named after its part. */
  static List<ConnectionInputResource> files(MultipartHttpServletRequest request) {
    var resources = new ArrayList<ConnectionInputResource>();
    request
        .getMultiFileMap()
        .forEach(
            (name, files) -> {
              if (BODY_PART.equals(name)) {
                return;
              }
              requireNamed(name);
              files.forEach(
                  file -> resources.add(MultipartConnectionInputResourceAdapter.adapt(name, file)));
            });
    return List.copyOf(resources);
  }

  /** The parts that carry no file. */
  private static List<Part> textParts(MultipartHttpServletRequest request) {
    try {
      return request.getParts().stream()
          .filter(part -> part.getSubmittedFileName() == null)
          .toList();
    } catch (IOException | ServletException _) {
      throw new ConnectionManagementException(ConnectionExceptionCodes.INVALID_REQUEST_BODY);
    }
  }

  private static ConnectionManagementException unexpectedTextPart(String name) {
    requireNamed(name);
    return ConnectionManagementException.forField(
        ConnectionExceptionCodes.UNKNOWN_REQUEST_FIELD, name, name);
  }

  private static void requireNamed(String name) {
    if (name == null || name.isBlank()) {
      throw new ConnectionManagementException(ConnectionExceptionCodes.BLANK_CONNECTION_INPUT_NAME);
    }
  }

  private static String text(MultipartFile part) {
    try {
      return read(part.getInputStream());
    } catch (IOException _) {
      throw new ConnectionManagementException(ConnectionExceptionCodes.INVALID_REQUEST_BODY);
    }
  }

  private static String text(Part part) {
    try {
      return read(part.getInputStream());
    } catch (IOException _) {
      throw new ConnectionManagementException(ConnectionExceptionCodes.INVALID_REQUEST_BODY);
    }
  }

  private static String read(InputStream content) throws IOException {
    try (content) {
      return new String(content.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
