// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.layer;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.business.policy.ResourceLifecyclePolicy;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.cache.EventGeoCacheEvictionGateway;
import com.opencop.core.api.general.technical.service.LayerAuthorizationService;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class ArchiveLayerUsecase {

  private static final Logger log = LoggerFactory.getLogger(ArchiveLayerUsecase.class);
  private static final String EVICTION_FAILED_MESSAGE =
      "Cached events could not be evicted. The layer was restored so the archive can be retried,"
          + " but some of its cached events may already have been removed.";
  private static final String RESTORE_FAILED_MESSAGE =
      "Cached events could not be evicted and the layer could not be restored.";

  private final LayerManager<Layer, LayerMember> layerManager;
  private final LayerAuthorizationService layerAuth;
  private final EventGeoCacheEvictionGateway eventGeoCacheEvictionGateway;
  private final AuditRecorder auditRecorder;
  private final LayerAuditProjection projection;
  private final TransactionTemplate transactionTemplate;

  public ArchiveLayerUsecase(
      LayerManager<Layer, LayerMember> layerManager,
      LayerAuthorizationService layerAuth,
      EventGeoCacheEvictionGateway eventGeoCacheEvictionGateway,
      AuditRecorder auditRecorder,
      LayerAuditProjection projection,
      PlatformTransactionManager transactionManager) {
    this.layerManager = layerManager;
    this.layerAuth = layerAuth;
    this.eventGeoCacheEvictionGateway = eventGeoCacheEvictionGateway;
    this.auditRecorder = auditRecorder;
    this.projection = projection;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  public ArchiveLayerResult archiveLayer(UUID layerId, OpenCopPrincipal authentication) {
    var layer = this.layerManager.get(layerId);
    ResourceLifecyclePolicy.assertLayerActive(layer, "archive layer");
    layerAuth.assertLayerRole(layerId, authentication, LayerRole.OWNER);
    log.info("User {} archiving layer {}", authentication.internalUserId(), layerId);
    List<LayerMember> members = this.layerManager.listLayerMembers(layerId);
    // Deliberately no precheck for referencing watchboxes: enumerating them here would disclose
    // watchbox existence to a caller with layer-archive rights but not necessarily watchbox
    // visibility. V37's deferred trigger enforces the invariant at commit, and
    // GlobalExceptionManager translates its SQLSTATE into a generic 409.
    // The archive and its row commit together, so a layer is never archived without its history.
    // Eviction is not transactional, so it runs after the commit and a failure compensates below.
    this.transactionTemplate.executeWithoutResult(
        _ -> {
          this.layerManager.archive(layerId, authentication.internalUserId());
          record(layer, Operation.ARCHIVE, authentication);
        });
    evictGeoCacheOrRestore(layer, authentication);

    return new ArchiveLayerResult(members);
  }

  /**
   * Evicts the layer's geo cache entries, restoring the archived layer when that fails. The cached
   * entries are only reachable through the layer id, and an archived layer is invisible to every
   * read path, so leaving it archived removes the only way to ask for them again on demand. The
   * scheduled janitor still sweeps whatever remains indexed once it passes its staleness score;
   * entries that already lost their index membership are the ones nothing reaches. Restoring the
   * layer keeps it archivable so the caller can retry.
   *
   * <p>A thrown eviction is not proof that the cache is untouched: the bulk delete and the index
   * removal are separate commands, so the restored layer can come back active with some or all of
   * its cached events gone, and no read path rebuilds that projection. The failure message promises
   * a retryable layer rather than an unchanged one for that reason.
   */
  private void evictGeoCacheOrRestore(Layer layer, OpenCopPrincipal authentication) {
    var layerId = layer.getId();
    try {
      this.eventGeoCacheEvictionGateway.evictGeoSource(layerId);
    } catch (Exception evictionFailure) {
      restoreArchivedLayer(layer, authentication, evictionFailure);
      throw new GlobalOpenCopException(EVICTION_FAILED_MESSAGE, HttpStatus.SERVICE_UNAVAILABLE);
    }
  }

  private void restoreArchivedLayer(
      Layer layer, OpenCopPrincipal authentication, Exception evictionFailure) {
    var layerId = layer.getId();
    try {
      // The restore is a second state change, so it leaves its own row: history then reads archive
      // followed by unarchive, which is what happened.
      this.transactionTemplate.executeWithoutResult(
          _ -> {
            this.layerManager.unarchive(layerId);
            record(layer, Operation.UNARCHIVE, authentication);
          });
    } catch (Exception restoreFailure) {
      restoreFailure.addSuppressed(evictionFailure);
      log.error(
          "Redis geo cache eviction failed for layer {} and restoring it failed too — the layer"
              + " stays archived, so whatever remains of its cached events can no longer be evicted"
              + " on demand",
          layerId,
          restoreFailure);
      throw new GlobalOpenCopException(RESTORE_FAILED_MESSAGE, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    log.warn(
        "Redis geo cache eviction failed for layer {}, restored it so the archive can be retried:"
            + " {}",
        layerId,
        evictionFailure.getMessage(),
        evictionFailure);
  }

  private void record(Layer layer, Operation operation, OpenCopPrincipal authentication) {
    this.auditRecorder.record(
        new AuditEvent(
            EntityType.LAYER,
            layer.getId(),
            layer.getName(),
            operation,
            Caller.of(authentication),
            this.projection.lifecycle(layer),
            AuditEvent.CURRENT_CHANGE_VERSION));
  }

  public record ArchiveLayerResult(List<LayerMember> members) {}
}
