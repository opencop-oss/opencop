// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.business.policy;

import static com.opencop.core.sdk.model.watchbox.WatchboxErrorCode.WATCHBOX_TRACKING_RESET_CONFIRMATION_REQUIRED;

import com.opencop.core.api.watchbox.business.model.WatchboxConfig;
import com.opencop.core.api.watchbox.business.service.WatchboxException;
import com.opencop.core.sdk.model.watchbox.WatchboxPatchRequest;
import com.opencop.core.sdk.model.watchbox.WatchboxResetReason;
import java.util.ArrayList;
import java.util.List;
import org.geojson.LngLatAlt;
import org.geojson.Polygon;
import org.springframework.http.HttpStatus;

public class WatchboxPatchPolicy {

  public boolean isCleanupOnlyPatch(WatchboxConfig existing, WatchboxConfig candidate) {
    boolean sameName = java.util.Objects.equals(existing.name(), candidate.name());
    boolean sameDescription =
        java.util.Objects.equals(existing.description(), candidate.description());
    boolean sameDirection = existing.direction() == candidate.direction();
    boolean samePolygon = polygonEquals(existing.polygon(), candidate.polygon());
    boolean sameFilter = java.util.Objects.equals(existing.filter(), candidate.filter());
    boolean sameLayers = java.util.Objects.equals(existing.layerIds(), candidate.layerIds());
    boolean sameEnabled = existing.enabled() == candidate.enabled();
    boolean onlyDisabling =
        existing.enabled()
            && !candidate.enabled()
            && sameName
            && sameDescription
            && sameDirection
            && samePolygon
            && sameFilter
            && sameLayers;
    boolean alreadyDisabledNoOp =
        !existing.enabled()
            && !candidate.enabled()
            && sameName
            && sameDescription
            && sameDirection
            && samePolygon
            && sameFilter
            && sameLayers
            && sameEnabled;
    return onlyDisabling || alreadyDisabledNoOp;
  }

  public PatchDecision decide(
      WatchboxConfig existing,
      WatchboxConfig candidate,
      WatchboxPatchRequest request,
      boolean cleanupOnly) {
    var resetFields = new ArrayList<String>();
    var resetReasons = new ArrayList<String>();
    if (request.polygon() != null && !polygonEquals(existing.polygon(), candidate.polygon())) {
      resetFields.add("polygon");
      resetReasons.add(WatchboxResetReason.POLYGON_CHANGED.name());
    }
    if (request.layerIds() != null
        && existing.layerIds().stream().anyMatch(id -> !candidate.layerIds().contains(id))) {
      resetFields.add("layerIds");
      resetReasons.add(WatchboxResetReason.LAYER_REMOVED.name());
    }
    if (!existing.enabled() && candidate.enabled()) {
      resetFields.add("enabled");
      resetReasons.add(WatchboxResetReason.REENABLED.name());
    }
    if (!resetFields.isEmpty() && !Boolean.TRUE.equals(request.confirmTrackingReset())) {
      throw new WatchboxException(
          WATCHBOX_TRACKING_RESET_CONFIRMATION_REQUIRED,
          "Watchbox tracking reset confirmation is required",
          HttpStatus.CONFLICT,
          resetFields,
          resetReasons);
    }
    long nextEpoch = existing.configEpoch() + (resetFields.isEmpty() ? 0 : 1);
    return new PatchDecision(cleanupOnly, resetFields, resetReasons, nextEpoch);
  }

  private static boolean polygonEquals(Polygon left, Polygon right) {
    if (left == null || right == null) {
      return left == right;
    }
    if (hasInteriorRings(left) || hasInteriorRings(right)) {
      return false;
    }
    List<LngLatAlt> leftRing = left.getExteriorRing();
    List<LngLatAlt> rightRing = right.getExteriorRing();
    if (leftRing == null || rightRing == null || leftRing.size() != rightRing.size()) {
      return false;
    }
    for (int i = 0; i < leftRing.size(); i++) {
      LngLatAlt leftPoint = leftRing.get(i);
      LngLatAlt rightPoint = rightRing.get(i);
      if (leftPoint == null
          || rightPoint == null
          || Double.compare(leftPoint.getLongitude(), rightPoint.getLongitude()) != 0
          || Double.compare(leftPoint.getLatitude(), rightPoint.getLatitude()) != 0) {
        return false;
      }
    }
    return true;
  }

  private static boolean hasInteriorRings(Polygon polygon) {
    return polygon.getInteriorRings() != null && !polygon.getInteriorRings().isEmpty();
  }

  public record PatchDecision(
      boolean cleanupOnly, List<String> resetFields, List<String> resetReasons, long nextEpoch) {

    public PatchDecision {
      resetFields = List.copyOf(resetFields);
      resetReasons = List.copyOf(resetReasons);
    }
  }
}
