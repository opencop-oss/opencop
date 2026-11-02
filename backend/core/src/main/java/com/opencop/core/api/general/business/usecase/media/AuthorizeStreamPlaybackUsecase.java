// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.media;

import com.opencop.core.api.event.business.policy.EventAttachmentPolicy;
import com.opencop.core.sdk.ports.gateway.media.EventAttachmentRepository;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AuthorizeStreamPlaybackUsecase {

  private final EventAttachmentRepository eventAttachmentRepository;
  private final EventAttachmentPolicy attachmentPolicy;
  private final List<StreamableAttachmentResolver> streamableAttachmentResolvers;
  private final StreamPlaybackReadiness streamPlaybackReadiness;

  public AuthorizeStreamPlaybackUsecase(
      EventAttachmentRepository eventAttachmentRepository,
      EventAttachmentPolicy attachmentPolicy,
      List<StreamableAttachmentResolver> streamableAttachmentResolvers,
      StreamPlaybackReadiness streamPlaybackReadiness) {
    this.eventAttachmentRepository = eventAttachmentRepository;
    this.attachmentPolicy = attachmentPolicy;
    this.streamableAttachmentResolvers = List.copyOf(streamableAttachmentResolvers);
    this.streamPlaybackReadiness = streamPlaybackReadiness;
  }

  public StreamAuthorizationDecision authorize(
      OpenCopPrincipal principal, String forwardedUri, String forwardedMethod) {
    if (!isSupportedMethod(forwardedMethod)) {
      return StreamAuthorizationDecision.deny(StreamAuthorizationReason.UNSUPPORTED_METHOD);
    }
    if (!streamPlaybackReadiness.ready()) {
      return StreamAuthorizationDecision.deny(StreamAuthorizationReason.MEDIAMTX_DISABLED);
    }

    var parsedAttachmentId = parseAttachmentId(forwardedUri);
    if (parsedAttachmentId.denied()) {
      return StreamAuthorizationDecision.deny(parsedAttachmentId.denialReason());
    }

    var attachmentId = parsedAttachmentId.attachmentId();
    var associations = eventAttachmentRepository.findAssociationsByAttachmentId(attachmentId);
    if (associations.isEmpty()) {
      return StreamAuthorizationDecision.deny(StreamAuthorizationReason.ATTACHMENT_NOT_FOUND);
    }
    if (!isStreamable(attachmentId)) {
      return StreamAuthorizationDecision.deny(StreamAuthorizationReason.ATTACHMENT_NOT_STREAMABLE);
    }

    for (var association : associations) {
      if (attachmentPolicy.canGet(
          principal, association.sourceId(), association.objectId(), association.attachmentId())) {
        return StreamAuthorizationDecision.allow();
      }
    }
    return StreamAuthorizationDecision.deny(StreamAuthorizationReason.NO_ACCESSIBLE_ASSOCIATION);
  }

  private ParsedAttachmentId parseAttachmentId(String forwardedUri) {
    if (forwardedUri == null || forwardedUri.isBlank()) {
      return ParsedAttachmentId.denied(StreamAuthorizationReason.MISSING_FORWARDED_URI);
    }
    var path = forwardedUri.split("[?#]", 2)[0];
    if (!isCanonicalPath(path)) {
      return ParsedAttachmentId.denied(StreamAuthorizationReason.MALFORMED_STREAM_PATH);
    }
    return AttachmentStreamPath.parseForwardedPath(path)
        .map(ParsedAttachmentId::allowed)
        .orElseGet(
            () -> ParsedAttachmentId.denied(StreamAuthorizationReason.MALFORMED_STREAM_PATH));
  }

  private static boolean isCanonicalPath(String path) {
    if (path.contains("\\") || path.contains("//") || containsEncodedPathSeparator(path)) {
      return false;
    }
    try {
      return !containsDotSegment(path);
    } catch (IllegalArgumentException _) {
      return false;
    }
  }

  private static boolean containsEncodedPathSeparator(String path) {
    var lowerCasePath = path.toLowerCase(Locale.ROOT);
    return lowerCasePath.contains("%2f") || lowerCasePath.contains("%5c");
  }

  private static boolean containsDotSegment(String path) {
    for (var segment : path.split("/", -1)) {
      if (segment.isEmpty()) {
        continue;
      }
      var decodedSegment = URLDecoder.decode(segment, StandardCharsets.UTF_8);
      if (segment.equals(".")
          || segment.equals("..")
          || decodedSegment.equals(".")
          || decodedSegment.equals("..")) {
        return true;
      }
    }
    return false;
  }

  private boolean isStreamable(UUID attachmentId) {
    return streamableAttachmentResolvers.stream()
        .map(resolver -> resolver.resolve(attachmentId))
        .flatMap(Optional::stream)
        .findFirst()
        .isPresent();
  }

  private static boolean isSupportedMethod(String forwardedMethod) {
    if (forwardedMethod == null || forwardedMethod.isBlank()) {
      return false;
    }
    return forwardedMethod.equalsIgnoreCase("GET") || forwardedMethod.equalsIgnoreCase("HEAD");
  }

  private record ParsedAttachmentId(UUID attachmentId, StreamAuthorizationReason denialReason) {

    static ParsedAttachmentId allowed(UUID attachmentId) {
      return new ParsedAttachmentId(attachmentId, null);
    }

    static ParsedAttachmentId denied(StreamAuthorizationReason reason) {
      return new ParsedAttachmentId(null, reason);
    }

    boolean denied() {
      return denialReason != null;
    }
  }
}
