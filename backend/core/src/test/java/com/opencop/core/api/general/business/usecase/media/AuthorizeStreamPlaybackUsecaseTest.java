// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.event.business.policy.EventAttachmentPolicy;
import com.opencop.core.sdk.ports.gateway.media.EventAttachmentAssociation;
import com.opencop.core.sdk.ports.gateway.media.EventAttachmentRepository;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthorizeStreamPlaybackUsecaseTest {

  private static final UUID ATTACHMENT_ID = UUID.fromString("6d063523-0461-439e-b025-ed3baf3b1d18");
  private static final UUID OTHER_ATTACHMENT_ID =
      UUID.fromString("3fb32875-c09f-46dd-ab1f-29f350fa15db");
  private static final UUID SOURCE_A = UUID.fromString("550e8400-e29b-41d4-a716-446655440201");
  private static final UUID SOURCE_B = UUID.fromString("550e8400-e29b-41d4-a716-446655440202");
  private static final UUID ARCHIVED_SOURCE =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440203");
  private static final UUID ARCHIVED_LAYER =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440204");
  private static final String OBJECT_A = "event-a";
  private static final String OBJECT_B = "event-b";
  private static final String STALE_OBJECT = "event-stale";

  private final EventAttachmentRepository repository = mock(EventAttachmentRepository.class);
  private final EventAttachmentPolicy attachmentPolicy = mock(EventAttachmentPolicy.class);
  private final StreamableAttachmentResolver streamableAttachmentResolver =
      mock(StreamableAttachmentResolver.class);
  private final StreamPlaybackReadiness streamPlaybackReadiness =
      mock(StreamPlaybackReadiness.class);

  private AuthorizeStreamPlaybackUsecase usecase;
  private OpenCopPrincipal principal;

  @BeforeEach
  void setUp() {
    usecase =
        new AuthorizeStreamPlaybackUsecase(
            repository,
            attachmentPolicy,
            List.of(streamableAttachmentResolver),
            streamPlaybackReadiness);
    principal =
        com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(UUID.randomUUID());
    when(streamPlaybackReadiness.ready()).thenReturn(true);
    when(streamableAttachmentResolver.resolve(ATTACHMENT_ID))
        .thenReturn(
            Optional.of(
                new StreamableAttachment(
                    ATTACHMENT_ID,
                    "att_" + ATTACHMENT_ID,
                    StreamableAttachmentSourceType.RTSP_URL,
                    "rtsp://camera/stream")));
  }

  @Test
  void authorize_whenUserCanReadAssociatedStreamableAttachment_allowsPlayback() {
    when(repository.findAssociationsByAttachmentId(ATTACHMENT_ID))
        .thenReturn(List.of(new EventAttachmentAssociation(SOURCE_A, OBJECT_A, ATTACHMENT_ID)));
    when(attachmentPolicy.canGet(principal, SOURCE_A, OBJECT_A, ATTACHMENT_ID)).thenReturn(true);

    var decision =
        usecase.authorize(principal, "/streams/att_" + ATTACHMENT_ID + "/index.m3u8", "GET");

    assertTrue(decision.allowed());
    assertEquals(StreamAuthorizationReason.ALLOWED, decision.reason());
    verify(attachmentPolicy).canGet(principal, SOURCE_A, OBJECT_A, ATTACHMENT_ID);
  }

  @Test
  void authorize_whenSharedAttachmentHasAccessibleAssociation_allowsPlayback() {
    when(repository.findAssociationsByAttachmentId(ATTACHMENT_ID))
        .thenReturn(
            List.of(
                new EventAttachmentAssociation(SOURCE_A, OBJECT_A, ATTACHMENT_ID),
                new EventAttachmentAssociation(SOURCE_B, OBJECT_B, ATTACHMENT_ID)));
    when(attachmentPolicy.canGet(principal, SOURCE_A, OBJECT_A, ATTACHMENT_ID)).thenReturn(false);
    when(attachmentPolicy.canGet(principal, SOURCE_B, OBJECT_B, ATTACHMENT_ID)).thenReturn(true);

    var decision =
        usecase.authorize(principal, "/streams/att_" + ATTACHMENT_ID + "/segment.ts", "HEAD");

    assertTrue(decision.allowed());
    assertEquals(StreamAuthorizationReason.ALLOWED, decision.reason());
    verify(attachmentPolicy).canGet(principal, SOURCE_A, OBJECT_A, ATTACHMENT_ID);
    verify(attachmentPolicy).canGet(principal, SOURCE_B, OBJECT_B, ATTACHMENT_ID);
  }

  @Test
  void authorize_whenSharedAttachmentHasNoAccessibleAssociation_deniesPlayback() {
    when(repository.findAssociationsByAttachmentId(ATTACHMENT_ID))
        .thenReturn(
            List.of(
                new EventAttachmentAssociation(SOURCE_A, OBJECT_A, ATTACHMENT_ID),
                new EventAttachmentAssociation(SOURCE_B, OBJECT_B, ATTACHMENT_ID)));
    when(attachmentPolicy.canGet(principal, SOURCE_A, OBJECT_A, ATTACHMENT_ID)).thenReturn(false);
    when(attachmentPolicy.canGet(principal, SOURCE_B, OBJECT_B, ATTACHMENT_ID)).thenReturn(false);

    var decision =
        usecase.authorize(principal, "/streams/att_" + ATTACHMENT_ID + "/segment.ts", "GET");

    assertFalse(decision.allowed());
    assertEquals(StreamAuthorizationReason.NO_ACCESSIBLE_ASSOCIATION, decision.reason());
    verify(attachmentPolicy).canGet(principal, SOURCE_A, OBJECT_A, ATTACHMENT_ID);
    verify(attachmentPolicy).canGet(principal, SOURCE_B, OBJECT_B, ATTACHMENT_ID);
  }

  @Test
  void authorize_whenStaleEventAssociationIsReadable_allowsPlayback() {
    when(repository.findAssociationsByAttachmentId(ATTACHMENT_ID))
        .thenReturn(List.of(new EventAttachmentAssociation(SOURCE_A, STALE_OBJECT, ATTACHMENT_ID)));
    when(attachmentPolicy.canGet(principal, SOURCE_A, STALE_OBJECT, ATTACHMENT_ID))
        .thenReturn(true);

    var decision =
        usecase.authorize(principal, "/streams/att_" + ATTACHMENT_ID + "/segment.ts", "GET");

    assertTrue(decision.allowed());
    assertEquals(StreamAuthorizationReason.ALLOWED, decision.reason());
  }

  @Test
  void authorize_whenArchivedDataSourceAssociationIsReadable_allowsPlayback() {
    when(repository.findAssociationsByAttachmentId(ATTACHMENT_ID))
        .thenReturn(
            List.of(new EventAttachmentAssociation(ARCHIVED_SOURCE, OBJECT_A, ATTACHMENT_ID)));
    when(attachmentPolicy.canGet(principal, ARCHIVED_SOURCE, OBJECT_A, ATTACHMENT_ID))
        .thenReturn(true);

    var decision =
        usecase.authorize(principal, "/streams/att_" + ATTACHMENT_ID + "/segment.ts", "GET");

    assertTrue(decision.allowed());
    assertEquals(StreamAuthorizationReason.ALLOWED, decision.reason());
  }

  @Test
  void authorize_whenArchivedLayerAssociationIsReadable_allowsPlayback() {
    when(repository.findAssociationsByAttachmentId(ATTACHMENT_ID))
        .thenReturn(
            List.of(new EventAttachmentAssociation(ARCHIVED_LAYER, OBJECT_A, ATTACHMENT_ID)));
    when(attachmentPolicy.canGet(principal, ARCHIVED_LAYER, OBJECT_A, ATTACHMENT_ID))
        .thenReturn(true);

    var decision =
        usecase.authorize(principal, "/streams/att_" + ATTACHMENT_ID + "/segment.ts", "GET");

    assertTrue(decision.allowed());
    assertEquals(StreamAuthorizationReason.ALLOWED, decision.reason());
  }

  @Test
  void authorize_whenForwardedUriIsMissing_deniesWithMissingForwardedUriReason() {
    var decision = usecase.authorize(principal, null, "GET");

    assertFalse(decision.allowed());
    assertEquals(StreamAuthorizationReason.MISSING_FORWARDED_URI, decision.reason());
    verify(repository, never()).findAssociationsByAttachmentId(ATTACHMENT_ID);
  }

  @Test
  void
      authorize_whenForwardedUriDoesNotContainStreamAttachmentPath_deniesWithMalformedPathReason() {
    var decision = usecase.authorize(principal, "/streams/ds_abc/index.m3u8", "GET");

    assertFalse(decision.allowed());
    assertEquals(StreamAuthorizationReason.MALFORMED_STREAM_PATH, decision.reason());
    verify(repository, never()).findAssociationsByAttachmentId(ATTACHMENT_ID);
  }

  @Test
  void authorize_whenForwardedUriContainsTraversalSegment_deniesBeforeAttachmentLookup() {
    for (var forwardedUri :
        List.of(
            "/streams/att_" + ATTACHMENT_ID + "/../att_" + OTHER_ATTACHMENT_ID + "/index.m3u8",
            "/streams/att_" + ATTACHMENT_ID + "/.%2e/att_" + OTHER_ATTACHMENT_ID + "/index.m3u8",
            "/streams/att_"
                + ATTACHMENT_ID
                + "/%2e%2e/att_"
                + OTHER_ATTACHMENT_ID
                + "/index.m3u8")) {
      var decision = usecase.authorize(principal, forwardedUri, "GET");

      assertFalse(decision.allowed());
      assertEquals(StreamAuthorizationReason.MALFORMED_STREAM_PATH, decision.reason());
    }
    verify(repository, never()).findAssociationsByAttachmentId(ATTACHMENT_ID);
    verify(repository, never()).findAssociationsByAttachmentId(OTHER_ATTACHMENT_ID);
  }

  @Test
  void authorize_whenForwardedUriContainsEncodedSeparator_deniesBeforeAttachmentLookup() {
    for (var forwardedUri :
        List.of(
            "/streams/att_" + ATTACHMENT_ID + "/%2f/index.m3u8",
            "/streams/att_" + ATTACHMENT_ID + "/%5c/index.m3u8",
            "/streams/att_" + ATTACHMENT_ID + "/\\index.m3u8")) {
      var decision = usecase.authorize(principal, forwardedUri, "GET");

      assertFalse(decision.allowed());
      assertEquals(StreamAuthorizationReason.MALFORMED_STREAM_PATH, decision.reason());
    }
    verify(repository, never()).findAssociationsByAttachmentId(ATTACHMENT_ID);
  }

  @Test
  void authorize_whenForwardedUriContainsDuplicateSlash_deniesBeforeAttachmentLookup() {
    var decision =
        usecase.authorize(principal, "/streams/att_" + ATTACHMENT_ID + "//index.m3u8", "GET");

    assertFalse(decision.allowed());
    assertEquals(StreamAuthorizationReason.MALFORMED_STREAM_PATH, decision.reason());
    verify(repository, never()).findAssociationsByAttachmentId(ATTACHMENT_ID);
  }

  @Test
  void authorize_whenForwardedMethodIsUnsupported_deniesWithUnsupportedMethodReason() {
    for (var forwardedMethod : List.of("POST", " ")) {
      var decision =
          usecase.authorize(
              principal, "/streams/att_" + ATTACHMENT_ID + "/index.m3u8", forwardedMethod);

      assertFalse(decision.allowed());
      assertEquals(StreamAuthorizationReason.UNSUPPORTED_METHOD, decision.reason());
    }
    var decision =
        usecase.authorize(principal, "/streams/att_" + ATTACHMENT_ID + "/index.m3u8", null);

    assertFalse(decision.allowed());
    assertEquals(StreamAuthorizationReason.UNSUPPORTED_METHOD, decision.reason());
    verify(repository, never()).findAssociationsByAttachmentId(ATTACHMENT_ID);
  }

  @Test
  void authorize_whenMediaMtxDisabled_deniesWithMediaMtxDisabledReason() {
    when(streamPlaybackReadiness.ready()).thenReturn(false);

    var decision =
        usecase.authorize(principal, "/streams/att_" + ATTACHMENT_ID + "/index.m3u8", "GET");

    assertFalse(decision.allowed());
    assertEquals(StreamAuthorizationReason.MEDIAMTX_DISABLED, decision.reason());
    verify(repository, never()).findAssociationsByAttachmentId(ATTACHMENT_ID);
  }

  @Test
  void authorize_whenMediaMtxPublicHlsBaseUrlMissing_deniesWithMediaMtxDisabledReason() {
    when(streamPlaybackReadiness.ready()).thenReturn(false);

    var decision =
        usecase.authorize(principal, "/streams/att_" + ATTACHMENT_ID + "/index.m3u8", "GET");

    assertFalse(decision.allowed());
    assertEquals(StreamAuthorizationReason.MEDIAMTX_DISABLED, decision.reason());
    verify(repository, never()).findAssociationsByAttachmentId(ATTACHMENT_ID);
  }

  @Test
  void authorize_whenAttachmentIsUnknown_deniesWithAttachmentNotFoundReason() {
    when(repository.findAssociationsByAttachmentId(ATTACHMENT_ID)).thenReturn(List.of());

    var decision =
        usecase.authorize(principal, "/streams/att_" + ATTACHMENT_ID + "/index.m3u8", "GET");

    assertFalse(decision.allowed());
    assertEquals(StreamAuthorizationReason.ATTACHMENT_NOT_FOUND, decision.reason());
    verify(streamableAttachmentResolver, never()).resolve(ATTACHMENT_ID);
  }

  @Test
  void authorize_whenAttachmentIsNotStreamable_deniesWithAttachmentNotStreamableReason() {
    when(repository.findAssociationsByAttachmentId(ATTACHMENT_ID))
        .thenReturn(List.of(new EventAttachmentAssociation(SOURCE_A, OBJECT_A, ATTACHMENT_ID)));
    when(streamableAttachmentResolver.resolve(ATTACHMENT_ID)).thenReturn(Optional.empty());

    var decision =
        usecase.authorize(principal, "/streams/att_" + ATTACHMENT_ID + "/index.m3u8", "GET");

    assertFalse(decision.allowed());
    assertEquals(StreamAuthorizationReason.ATTACHMENT_NOT_STREAMABLE, decision.reason());
    verify(attachmentPolicy, never()).canGet(principal, SOURCE_A, OBJECT_A, ATTACHMENT_ID);
  }

  @Test
  void authorize_whenAssociatedFileAttachmentIsNotStreamable_deniesWithNotStreamableReason() {
    when(repository.findAssociationsByAttachmentId(ATTACHMENT_ID))
        .thenReturn(List.of(new EventAttachmentAssociation(SOURCE_A, OBJECT_A, ATTACHMENT_ID)));
    when(streamableAttachmentResolver.resolve(ATTACHMENT_ID)).thenReturn(Optional.empty());

    var decision =
        usecase.authorize(principal, "/streams/att_" + ATTACHMENT_ID + "/index.m3u8", "GET");

    assertFalse(decision.allowed());
    assertEquals(StreamAuthorizationReason.ATTACHMENT_NOT_STREAMABLE, decision.reason());
  }

  @Test
  void authorize_whenFutureResolverResolvesAttachmentWithoutUrlRepository_allowsPlayback() {
    var futureResolver = mock(StreamableAttachmentResolver.class);
    usecase =
        new AuthorizeStreamPlaybackUsecase(
            repository, attachmentPolicy, List.of(futureResolver), streamPlaybackReadiness);
    when(repository.findAssociationsByAttachmentId(ATTACHMENT_ID))
        .thenReturn(List.of(new EventAttachmentAssociation(SOURCE_A, OBJECT_A, ATTACHMENT_ID)));
    when(futureResolver.resolve(ATTACHMENT_ID))
        .thenReturn(
            Optional.of(
                new StreamableAttachment(
                    ATTACHMENT_ID,
                    "att_" + ATTACHMENT_ID,
                    StreamableAttachmentSourceType.RTSP_URL,
                    "custom://resolver-owned-source")));
    when(attachmentPolicy.canGet(principal, SOURCE_A, OBJECT_A, ATTACHMENT_ID)).thenReturn(true);

    var decision =
        usecase.authorize(principal, "/streams/att_" + ATTACHMENT_ID + "/index.m3u8", "GET");

    assertTrue(decision.allowed());
    assertEquals(StreamAuthorizationReason.ALLOWED, decision.reason());
  }

  @Test
  void authorize_whenNoAssociationIsAccessible_deniesWithNoAccessibleAssociationReason() {
    when(repository.findAssociationsByAttachmentId(ATTACHMENT_ID))
        .thenReturn(List.of(new EventAttachmentAssociation(SOURCE_A, OBJECT_A, ATTACHMENT_ID)));
    when(attachmentPolicy.canGet(principal, SOURCE_A, OBJECT_A, ATTACHMENT_ID)).thenReturn(false);

    var decision =
        usecase.authorize(principal, "/streams/att_" + ATTACHMENT_ID + "/index.m3u8", "GET");

    assertFalse(decision.allowed());
    assertEquals(StreamAuthorizationReason.NO_ACCESSIBLE_ASSOCIATION, decision.reason());
  }
}
