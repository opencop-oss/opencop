// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.technical.endpoint;

import com.opencop.core.api.event.business.policy.EventAttachmentPolicy;
import com.opencop.core.api.event.ports.business.usecase.attachment.CreateEventAttachment;
import com.opencop.core.api.event.ports.business.usecase.attachment.CreateEventAttachmentCommand;
import com.opencop.core.api.event.ports.business.usecase.attachment.CreateEventAttachmentCommand.EventAttachmentFile;
import com.opencop.core.api.event.ports.business.usecase.attachment.CreateEventAttachmentCommand.FilePayload;
import com.opencop.core.api.event.ports.business.usecase.attachment.CreateEventAttachmentCommand.UrlPayload;
import com.opencop.core.api.event.ports.business.usecase.attachment.EventAttachmentType;
import com.opencop.core.api.general.ports.usecase.media.AttachmentView;
import com.opencop.core.sdk.model.media.StreamKind;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.util.WebUtils;
import tools.jackson.databind.ObjectMapper;

@Component
public final class EventAttachmentCreateRegistry {

  private static final Logger log = LoggerFactory.getLogger(EventAttachmentCreateRegistry.class);

  record RegisterUrlRequest(String url, String streamKind) {}

  private final List<CreateEventAttachment> usecases;
  private final EventAttachmentPolicy attachmentPolicy;
  private final ObjectMapper objectMapper;

  EventAttachmentCreateRegistry(
      List<CreateEventAttachment> usecases,
      EventAttachmentPolicy attachmentPolicy,
      ObjectMapper objectMapper) {
    this.usecases = List.copyOf(usecases);
    this.attachmentPolicy = attachmentPolicy;
    this.objectMapper = objectMapper;
  }

  AttachmentView create(
      OpenCopPrincipal authentication, UUID sourceId, String objectId, HttpServletRequest request)
      throws IOException {
    var contentType = parseContentType(request);
    var attachmentType = attachmentType(contentType);
    var command =
        new CreateEventAttachmentCommand(
            authentication, sourceId, objectId, payload(attachmentType, request));
    var usecase =
        usecases.stream()
            .filter(candidate -> candidate.supports(contentType, attachmentType, command))
            .findFirst()
            .orElseThrow(
                () ->
                    new GlobalOpenCopException(
                        "Unsupported attachment content type: " + contentType,
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE));

    attachmentPolicy.assertCanCreate(authentication, sourceId, objectId);

    return usecase.create(command);
  }

  private static MediaType parseContentType(HttpServletRequest request) {
    var value = request.getContentType();
    if (value == null || value.isBlank()) {
      throw new GlobalOpenCopException("Missing Content-Type header", HttpStatus.BAD_REQUEST);
    }
    try {
      return MediaType.parseMediaType(value);
    } catch (IllegalArgumentException e) {
      throw new GlobalOpenCopException("Invalid Content-Type header", HttpStatus.BAD_REQUEST);
    }
  }

  private static EventAttachmentType attachmentType(MediaType contentType) {
    if (MediaType.MULTIPART_FORM_DATA.isCompatibleWith(contentType)) {
      return EventAttachmentType.FILE;
    }
    if (MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
      return EventAttachmentType.URL;
    }
    throw new GlobalOpenCopException(
        "Unsupported attachment content type: " + contentType, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
  }

  private CreateEventAttachmentCommand.Payload payload(
      EventAttachmentType attachmentType, HttpServletRequest request) throws IOException {
    return switch (attachmentType) {
      case FILE -> filePayload(request);
      case URL -> urlPayload(request);
    };
  }

  private static FilePayload filePayload(HttpServletRequest request) throws IOException {
    var multipart = WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class);
    if (multipart == null) {
      throw new GlobalOpenCopException(
          "Expected a multipart/form-data request", HttpStatus.BAD_REQUEST);
    }

    var original = multipart.getFile("original");
    if (original == null || original.isEmpty()) {
      throw new GlobalOpenCopException(
          "Missing required multipart part 'original'", HttpStatus.BAD_REQUEST);
    }

    return new FilePayload(
        new EventAttachmentFile(
            original.getOriginalFilename(),
            original.getContentType(),
            original.getSize(),
            original.getInputStream()),
        multipart.getFiles("thumbnails").stream()
            .map(
                thumb -> {
                  try {
                    return new EventAttachmentFile(
                        thumb.getOriginalFilename(),
                        thumb.getContentType(),
                        thumb.getSize(),
                        thumb.getInputStream());
                  } catch (IOException e) {
                    log.error(
                        "Failed to read attachment thumbnail {}", thumb.getOriginalFilename(), e);
                    throw new GlobalOpenCopException(
                        "Failed to read attachment thumbnail", HttpStatus.INTERNAL_SERVER_ERROR);
                  }
                })
            .toList());
  }

  private UrlPayload urlPayload(HttpServletRequest request) throws IOException {
    var body = objectMapper.readValue(request.getInputStream(), RegisterUrlRequest.class);
    if (body.url() == null || body.url().isBlank()) {
      throw new GlobalOpenCopException("url must not be blank", HttpStatus.BAD_REQUEST);
    }
    return new UrlPayload(body.url(), parseStreamKind(body.streamKind()));
  }

  /** Read as a string so an unknown value is a 400 rather than a Jackson deserialization 500. */
  private static StreamKind parseStreamKind(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return StreamKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException _) {
      throw new GlobalOpenCopException(
          "Unknown streamKind '"
              + sanitizeForMessage(raw)
              + "'. Allowed: "
              + Arrays.stream(StreamKind.values())
                  .map(Enum::name)
                  .collect(Collectors.joining(", ")),
          HttpStatus.BAD_REQUEST);
    }
  }

  private static String sanitizeForMessage(String raw) {
    var bounded = raw.length() > 64 ? raw.substring(0, 64) + "..." : raw;
    return bounded.replaceAll("\\p{Cntrl}", "?");
  }
}
