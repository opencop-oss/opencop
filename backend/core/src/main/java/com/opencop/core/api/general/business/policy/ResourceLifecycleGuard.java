// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy;

import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ResourceLifecycleGuard {

  private static final String RESOURCE_NOT_FOUND_MESSAGE = "Resource not found";

  private final DataSourceRepository dataSourceRepository;
  private final LayerManager<Layer, LayerMember> layerManager;

  public ResourceLifecycleGuard(
      DataSourceRepository dataSourceRepository, LayerManager<Layer, LayerMember> layerManager) {
    this.dataSourceRepository = dataSourceRepository;
    this.layerManager = layerManager;
  }

  public void assertDataSourceActive(UUID sourceId, String operation) {
    var dataSource =
        dataSourceRepository
            .findById(sourceId)
            .orElseThrow(
                () -> new GlobalOpenCopException(RESOURCE_NOT_FOUND_MESSAGE, HttpStatus.NOT_FOUND));
    ResourceLifecyclePolicy.assertDataSourceActive(dataSource, operation);
  }

  public void assertLayerActive(UUID layerId, String operation) {
    try {
      var layer = layerManager.get(layerId);
      ResourceLifecyclePolicy.assertLayerActive(layer, operation);
    } catch (GlobalOpenCopException e) {
      if (e.getHttpStatus() == HttpStatus.NOT_FOUND) {
        throw new GlobalOpenCopException(RESOURCE_NOT_FOUND_MESSAGE, HttpStatus.NOT_FOUND);
      }
      throw e;
    }
  }

  public void assertEventResourceActive(UUID sourceId, String operation) {
    var dataSource = dataSourceRepository.findById(sourceId);
    if (dataSource.isPresent()) {
      ResourceLifecyclePolicy.assertDataSourceActive(dataSource.get(), operation);
      return;
    }
    assertLayerActive(sourceId, operation);
  }

  /**
   * Verifies the data source exists without enforcing the active-lifecycle rule. Use when a
   * read-only path (e.g., historical export) must reject unknown ids with {@code 404} but is
   * allowed to operate on archived rows. Operational mutations must keep using {@link
   * #assertDataSourceActive(UUID, String)}.
   */
  public void assertDataSourceExists(UUID sourceId) {
    if (dataSourceRepository.findById(sourceId).isEmpty()) {
      throw new GlobalOpenCopException(RESOURCE_NOT_FOUND_MESSAGE, HttpStatus.NOT_FOUND);
    }
  }

  /**
   * Verifies the layer exists without enforcing the active-lifecycle rule. Use when a read-only
   * path (e.g., historical export) must reject unknown ids with {@code 404} but is allowed to
   * operate on archived layers. Operational mutations must keep using {@link
   * #assertLayerActive(UUID, String)}.
   */
  public void assertLayerExists(UUID layerId) {
    try {
      layerManager.get(layerId);
    } catch (GlobalOpenCopException e) {
      if (e.getHttpStatus() == HttpStatus.NOT_FOUND) {
        throw new GlobalOpenCopException(RESOURCE_NOT_FOUND_MESSAGE, HttpStatus.NOT_FOUND);
      }
      throw e;
    }
  }
}
