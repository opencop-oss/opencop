// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.business.usecase;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.opencop.core.api.event.business.model.EventDetails;
import com.opencop.core.api.event.business.policy.EventAuthorizationPolicy;
import com.opencop.core.api.event.ports.gateway.PostGisService;
import com.opencop.core.api.watchbox.business.model.*;
import com.opencop.core.api.watchbox.business.policy.WatchboxLayerAccessPolicy;
import com.opencop.core.api.watchbox.business.service.WatchboxException;
import com.opencop.core.api.watchbox.ports.gateway.*;
import com.opencop.core.api.watchbox.ports.usecase.WatchboxCaller;
import com.opencop.core.sdk.model.watchbox.WatchboxTransitionKind;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReadWatchboxDetectionsUsecaseTest {
  private final WatchboxConfigRepository repository = mock(WatchboxConfigRepository.class);
  private final WatchboxDetectionReader reader = mock(WatchboxDetectionReader.class);
  private final WatchboxLayerAccess layerAccess = mock(WatchboxLayerAccess.class);
  private final EventAuthorizationPolicy eventPolicy = mock(EventAuthorizationPolicy.class);
  private final PostGisService events = mock(PostGisService.class);
  private final WatchboxCaller caller = new WatchboxCaller(UUID.randomUUID(), false);
  private final UUID boxId = UUID.randomUUID(),
      layer = UUID.randomUUID(),
      source = UUID.randomUUID();
  private final WatchboxDetectionPageRequest page = new WatchboxDetectionPageRequest(0, 25);
  private ReadWatchboxDetectionsUsecase usecase;

  @BeforeEach
  void setup() {
    usecase =
        new ReadWatchboxDetectionsUsecase(
            repository, new WatchboxLayerAccessPolicy(layerAccess), reader, eventPolicy, events);
  }

  @Test
  void list_withoutFilter_readsOnlyFullyVisibleBoxes() {
    var hiddenLayer = UUID.randomUUID();
    when(repository.listActive())
        .thenReturn(
            List.of(
                config(boxId, Set.of(layer)),
                config(UUID.randomUUID(), Set.of(layer, hiddenLayer))));
    when(layerAccess.rolesForLayers(Set.of(layer, hiddenLayer), caller))
        .thenReturn(Map.of(layer, WatchboxLayerRole.MEMBER));
    usecase.list(new WatchboxDetectionFilter(null, Set.of()), page, caller);
    verify(reader).findPage(Set.of(boxId), Set.of(), page);
  }

  @Test
  void list_withoutFilter_whenNothingVisible_returnsEmptyPageWithoutReading() {
    when(repository.listActive()).thenReturn(List.of(config(boxId, Set.of(layer))));
    var result = usecase.list(new WatchboxDetectionFilter(null, Set.of()), page, caller);
    assertThat(result).isEqualTo(new PageResponse<>(List.of(), 0, 25, 0, 0));
    verifyNoInteractions(reader);
  }

  @Test
  void list_withBoxFilter_whenNotVisible_throwsWatchboxNotFound() {
    when(repository.findActive(boxId)).thenReturn(Optional.of(config(boxId, Set.of(layer))));
    assertThatThrownBy(
            () -> usecase.list(new WatchboxDetectionFilter(boxId, Set.of()), page, caller))
        .isInstanceOf(WatchboxException.class)
        .hasMessage("Watchbox not found");
    verifyNoInteractions(reader);
  }

  @Test
  void list_passesKindsAndPageableToReader() {
    when(repository.findActive(boxId)).thenReturn(Optional.of(config(boxId, Set.of(layer))));
    when(layerAccess.rolesForLayers(Set.of(layer), caller))
        .thenReturn(Map.of(layer, WatchboxLayerRole.MEMBER));
    var kinds = Set.of(WatchboxTransitionKind.ENTER);
    var selected = new WatchboxDetectionPageRequest(2, 5);
    usecase.list(new WatchboxDetectionFilter(boxId, kinds), selected, caller);
    verify(reader).findPage(Set.of(boxId), kinds, selected);
  }

  @Test
  void get_whenSourceNotViewable_returnsEmptyEmbedsWithoutFeatureReads() {
    var d = seedGet();
    var result = usecase.get(d.id(), caller);
    assertThat(result.detection()).isEqualTo(d);
    assertThat(result.triggeringEvent()).isEmpty();
    assertThat(result.latestEvent()).isEmpty();
    verifyNoInteractions(events);
  }

  @Test
  void get_whenFeaturesPresent_returnsBothEmbeds() throws Exception {
    var d = seedGet();
    when(eventPolicy.canViewSource(caller.userId(), false, source)).thenReturn(true);
    var trigger = new EventDetails(new OpenCopProperties(), null);
    var latest = new EventDetails(new OpenCopProperties(), null);
    when(events.findEventDetails(source, d.objectId(), d.eventId()))
        .thenReturn(Optional.of(trigger));
    when(events.findLatestEventDetails(source, d.objectId())).thenReturn(Optional.of(latest));
    assertThat(usecase.get(d.id(), caller))
        .isEqualTo(new WatchboxDetectionDetail(d, Optional.of(trigger), Optional.of(latest)));
  }

  @Test
  void get_whenOneEmbedIsUnreadable_returnsThatEmbedEmpty_andKeepsTheOther() throws Exception {
    var d = seedGet();
    when(eventPolicy.canViewSource(caller.userId(), false, source)).thenReturn(true);
    when(events.findEventDetails(source, d.objectId(), d.eventId()))
        .thenThrow(new PersistenceException("Could not parse properties"));
    var latest = new EventDetails(new OpenCopProperties(), null);
    when(events.findLatestEventDetails(source, d.objectId())).thenReturn(Optional.of(latest));
    assertThat(usecase.get(d.id(), caller))
        .isEqualTo(new WatchboxDetectionDetail(d, Optional.empty(), Optional.of(latest)));
  }

  @Test
  void get_whenDetectionMissing_throwsWatchboxNotFound() {
    assertThatThrownBy(() -> usecase.get(UUID.randomUUID(), caller))
        .isInstanceOf(WatchboxException.class)
        .hasMessage("Watchbox not found");
    verifyNoInteractions(repository, layerAccess, eventPolicy, events);
  }

  private WatchboxDetection seedGet() {
    var d =
        new WatchboxDetection(
            UUID.randomUUID(),
            boxId,
            1,
            source,
            "track",
            UUID.randomUUID(),
            Instant.EPOCH,
            WatchboxTransitionKind.ENTER,
            null,
            Instant.EPOCH);
    when(reader.findById(d.id())).thenReturn(Optional.of(d));
    when(repository.findActive(boxId)).thenReturn(Optional.of(config(boxId, Set.of(layer))));
    when(layerAccess.rolesForLayers(Set.of(layer), caller))
        .thenReturn(Map.of(layer, WatchboxLayerRole.MEMBER));
    return d;
  }

  private WatchboxConfig config(UUID id, Set<UUID> layers) {
    return new WatchboxConfig(
        id,
        "box",
        null,
        true,
        com.opencop.core.sdk.model.watchbox.WatchboxDirection.BOTH,
        new org.geojson.Polygon(),
        null,
        layers,
        1,
        Instant.EPOCH,
        Instant.EPOCH);
  }
}
