// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.runtimefeature;

import static com.opencop.core.api.general.business.runtimefeature.RuntimeFeatureErrorCode.RUNTIME_FEATURE_BUSY;
import static com.opencop.core.api.general.business.runtimefeature.RuntimeFeatureErrorCode.RUNTIME_FEATURE_CLASS_CONFLICT;
import static com.opencop.core.api.general.business.runtimefeature.RuntimeFeatureErrorCode.RUNTIME_FEATURE_NOT_FOUND;
import static com.opencop.core.api.general.business.runtimefeature.RuntimeFeatureErrorCode.RUNTIME_FEATURE_REVISION_CONFLICT;

import com.opencop.core.api.general.business.model.runtimefeature.RuntimeFeatureReconciliation;
import com.opencop.core.api.general.business.model.runtimefeature.RuntimeFeatureRejectionReason;
import com.opencop.core.api.general.business.policy.runtimefeature.RuntimeFeatureAudiencePolicy;
import com.opencop.core.api.general.business.policy.runtimefeature.RuntimeFeatureNamePolicy;
import com.opencop.core.api.general.business.runtimefeature.RuntimeFeatureException;
import com.opencop.core.api.general.ports.gateway.runtimefeature.WatchboxTrackingResetGateway;
import com.opencop.core.api.general.ports.gateway.runtimefeature.WatchboxTrackingResetTelemetry;
import com.opencop.core.api.general.ports.usecase.runtimefeature.ManageRuntimeFeatures;
import com.opencop.core.sdk.model.runtimefeature.BackendRuntimeFeature;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureActor;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureAudience;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureConsumerClass;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureRegistrationResult;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureState;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureWriteResult;
import com.opencop.core.sdk.ports.gateway.runtimefeature.RuntimeFeatureBusyException;
import com.opencop.core.sdk.ports.gateway.runtimefeature.RuntimeFeatureMutationGateway;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

public class ManageRuntimeFeaturesUsecase implements ManageRuntimeFeatures {

  private static final Logger log = LoggerFactory.getLogger(ManageRuntimeFeaturesUsecase.class);

  private final RuntimeFeatureMutationGateway mutationGateway;
  private final RuntimeFeatureAudiencePolicy audiencePolicy;
  private final RuntimeFeatureNamePolicy namePolicy;
  private final WatchboxTrackingResetGateway trackingResetGateway;
  private final WatchboxTrackingResetTelemetry trackingResetTelemetry;

  public ManageRuntimeFeaturesUsecase(
      RuntimeFeatureMutationGateway mutationGateway,
      RuntimeFeatureAudiencePolicy audiencePolicy,
      RuntimeFeatureNamePolicy namePolicy,
      WatchboxTrackingResetGateway trackingResetGateway,
      WatchboxTrackingResetTelemetry trackingResetTelemetry) {
    this.mutationGateway = mutationGateway;
    this.audiencePolicy = audiencePolicy;
    this.namePolicy = namePolicy;
    this.trackingResetGateway = trackingResetGateway;
    this.trackingResetTelemetry = trackingResetTelemetry;
  }

  @Override
  @Transactional
  public RuntimeFeatureState replaceAudience(
      String name,
      RuntimeFeatureAudience audience,
      OptionalLong expectedRevision,
      RuntimeFeatureActor actor) {
    RuntimeFeatureWriteResult result;
    try {
      audiencePolicy.assertValidTargets(audience);
      result = mutationGateway.replaceAudience(name, audience, expectedRevision, actor);
    } catch (RuntimeFeatureBusyException _) {
      throw new RuntimeFeatureException(
          RUNTIME_FEATURE_BUSY,
          "Runtime feature or one of its target groups is locked by a concurrent operation; retry",
          HttpStatus.CONFLICT);
    }
    return switch (result) {
      case RuntimeFeatureWriteResult.Applied applied -> {
        resetWatchboxTrackingIfReEnabled(name, applied);
        yield applied.state();
      }
      case RuntimeFeatureWriteResult.Idempotent idempotent -> idempotent.state();
      case RuntimeFeatureWriteResult.Unknown _ ->
          throw new RuntimeFeatureException(
              RUNTIME_FEATURE_NOT_FOUND,
              "Runtime feature is not registered: " + name,
              HttpStatus.NOT_FOUND);
      case RuntimeFeatureWriteResult.Stale stale ->
          throw new RuntimeFeatureException(
              RUNTIME_FEATURE_REVISION_CONFLICT,
              "Runtime feature revision conflict; current revision is " + stale.state().revision(),
              HttpStatus.CONFLICT);
    };
  }

  /**
   * Invalidates retained Watchbox tracking state when the Watchbox feature is re-enabled. No-op for
   * every other feature and every other audience transition.
   *
   * <p>After Processor runtime-config caches observe that Watchbox is disabled, the Processor
   * writes no transition state, so retained per-object inside/outside rows go stale. Bumping every
   * active watchbox's {@code config_epoch} starts a new generation for transition state,
   * detections, and source-status rows. Epoch-scoped source status and churn freezes from the
   * previous generation no longer affect the current response/runtime snapshot. Once a Processor
   * instance observes the new generation, its first observation seeds a fresh row and emits nothing
   * instead of comparing against a pre-disable value and reporting a crossing nobody observed;
   * {@code updated_at} is refreshed on every active Watchbox without moving backwards.
   *
   * <p>This call must remain inside {@link #replaceAudience}'s transaction. Committing the audience
   * change without the epoch bump would let the Processor resume against stale tracking state.
   *
   * <p>Re-enable means leaving {@link RuntimeFeatureAudience.None}, not arriving at {@link
   * RuntimeFeatureAudience.All}. The Processor treats every non-{@code NONE} audience as live, so
   * group-scoped rollouts need the same reset.
   */
  private void resetWatchboxTrackingIfReEnabled(
      String name, RuntimeFeatureWriteResult.Applied applied) {
    RuntimeFeatureAudience currentAudience = applied.state().audience();
    if (!isWatchbox(name) || !isReEnable(applied.previousAudience(), currentAudience)) {
      return;
    }
    int bumped = trackingResetGateway.bumpAllActiveConfigEpochs();
    trackingResetTelemetry.recordResetAfterCommit(currentAudience, bumped);
  }

  private static boolean isWatchbox(String name) {
    return BackendRuntimeFeature.WATCHBOX.wireName().equals(name);
  }

  private static boolean isReEnable(
      RuntimeFeatureAudience previous, RuntimeFeatureAudience current) {
    return previous instanceof RuntimeFeatureAudience.None
        && !(current instanceof RuntimeFeatureAudience.None);
  }

  /**
   * Deliberately <strong>not</strong> {@code @Transactional}, for the same reason {@link
   * #reconcile} is not: the store opens its own transaction, so an outer one adds no atomicity here
   * — one name is one store call — and would only delay the commit past the success log, letting it
   * announce a registration that had not yet been committed.
   */
  @Override
  public RuntimeFeatureRegistrationResult register(String name, RuntimeFeatureActor actor) {
    return switch (attemptRegistration(name, actor)) {
      case Attempt.Registered(var result) -> result;
      case Attempt.Refused(var reason) ->
          throw new RuntimeFeatureException(reason.code(), reason.message(), reason.status());
    };
  }

  /**
   * Deliberately <strong>not</strong> {@code @Transactional}.
   *
   * <p>Each name must succeed or fail on its own, and the store is reached through its bean proxy —
   * so without a transaction open here, every name gets its own, and a name that committed stays
   * committed no matter what a later name does.
   *
   * <p>An outer transaction would defeat that. The failure that breaks it is one raised
   * <em>inside</em> the store call: it propagates out through the store's own transactional
   * boundary, so a participating inner transaction marks the shared one rollback-only, and the
   * outer commit then fails wholesale — discarding names that had already registered and turning
   * "report what happened" into all-or-nothing. The refusals decided outside that call — a
   * malformed name, a reserved name, a class conflict — never reach a transactional boundary at
   * all, because they are returned as values rather than thrown.
   *
   * <p>An unexpected store failure is deliberately <em>not</em> converted into a per-name
   * rejection: it propagates, the request fails, and the partial progress is recorded in the abort
   * event rather than reported as a business outcome.
   */
  @Override
  public RuntimeFeatureReconciliation reconcile(List<String> names, RuntimeFeatureActor actor) {
    Objects.requireNonNull(names, "names is required");
    // Not a per-name rejection: a Rejection is keyed by name, so a null has nothing to report
    // under. Callers validate the envelope; this keeps the failure legible if one forgets.
    if (names.contains(null)) {
      throw new IllegalArgumentException("names must not contain null entries");
    }

    var created = new ArrayList<RuntimeFeatureState>();
    var alreadyRegistered = new ArrayList<RuntimeFeatureState>();
    var rejected = new ArrayList<RuntimeFeatureReconciliation.Rejection>();

    // LinkedHashSet: collapse duplicates but keep submission order, so the response reads in the
    // order the caller declared its manifest.
    var submitted = new LinkedHashSet<>(names);
    try {
      for (var name : submitted) {
        switch (attemptRegistration(name, actor)) {
          case Attempt.Registered(RuntimeFeatureRegistrationResult.Created(var state)) ->
              created.add(state);
          case Attempt.Registered(RuntimeFeatureRegistrationResult.AlreadyExists(var state)) ->
              alreadyRegistered.add(state);
          case Attempt.Refused(var reason) ->
              rejected.add(
                  new RuntimeFeatureReconciliation.Rejection(
                      name, reason.code(), reason.message()));
        }
      }
    } catch (RuntimeException exception) {
      // Fail-fast: an unexpected store or infrastructure failure is not a per-name business
      // outcome, so it propagates as a 5xx. Names already registered stay committed and later
      // names are unattempted, which is exactly what this event records for whoever reads the log.
      log.atError()
          .setMessage("Runtime feature reconciliation aborted")
          .addKeyValue("event", "runtime_feature.reconciliation_aborted")
          .addKeyValue("submitted", submitted.size())
          .addKeyValue("created", created.size())
          .addKeyValue("alreadyRegistered", alreadyRegistered.size())
          .addKeyValue("rejected", rejected.size())
          .addKeyValue("actorUserId", actor.userId())
          .log();
      throw exception;
    }

    // Counts only, never the names: a manifest is caller-supplied and capped at 200 entries, so a
    // per-name list would be an unbounded log line. Per-name detail is in the response body.
    log.atInfo()
        .setMessage("Runtime feature reconciliation completed")
        .addKeyValue("event", "runtime_feature.reconciled")
        .addKeyValue("submitted", submitted.size())
        .addKeyValue("created", created.size())
        .addKeyValue("alreadyRegistered", alreadyRegistered.size())
        .addKeyValue("rejected", rejected.size())
        .addKeyValue("complete", rejected.isEmpty())
        .addKeyValue("actorUserId", actor.userId())
        .log();

    return new RuntimeFeatureReconciliation(created, alreadyRegistered, rejected);
  }

  /**
   * One name's outcome: the stored result, or the reason the catalog refused it.
   *
   * <p>Refusals are values rather than exceptions because both callers need them but report them
   * differently — {@link #register} raises one, {@link #reconcile} records it and still returns
   * 200. Constructing a {@code RuntimeFeatureException} logs the HTTP status it carries, so the
   * reconcile path would otherwise announce a response it never sends.
   */
  private sealed interface Attempt {
    record Registered(RuntimeFeatureRegistrationResult result) implements Attempt {}

    record Refused(RuntimeFeatureRejectionReason reason) implements Attempt {}
  }

  private Attempt attemptRegistration(String name, RuntimeFeatureActor actor) {
    var nameRejection = namePolicy.checkRegistrable(name);
    if (nameRejection.isPresent()) {
      return new Attempt.Refused(nameRejection.get());
    }

    RuntimeFeatureRegistrationResult result;
    try {
      result = mutationGateway.register(name, actor);
    } catch (RuntimeFeatureBusyException _) {
      // Unreachable today: registration is a conflict-free insert that takes no lock, so only
      // replaceAudience can contend. Kept so a locking implementation of the register port refuses
      // one name rather than failing the batch. This is the only store failure with a per-name
      // meaning; every other one propagates.
      return new Attempt.Refused(
          new RuntimeFeatureRejectionReason(
              RUNTIME_FEATURE_BUSY,
              "Runtime feature catalog is locked by a concurrent operation; retry",
              HttpStatus.CONFLICT));
    }

    // An existing row under a name the policy allows can still be backend-owned — a future
    // migration may add a BACKEND_ENFORCED name that predates this build's BackendRuntimeFeature
    // enum. Registration never reclassifies, so this is a hard collision rather than convergence.
    var state = result.state();
    if (state.consumerClass() != RuntimeFeatureConsumerClass.FRONTEND_ONLY) {
      return new Attempt.Refused(
          new RuntimeFeatureRejectionReason(
              RUNTIME_FEATURE_CLASS_CONFLICT,
              "Runtime feature is already registered as " + state.consumerClass() + ": " + name,
              HttpStatus.CONFLICT));
    }

    // The store's transaction has committed by now — neither caller holds an outer one — so this
    // records a durable fact rather than an intention.
    switch (result) {
      case RuntimeFeatureRegistrationResult.Created _ ->
          log.atInfo()
              .setMessage("Runtime feature registered")
              .addKeyValue("event", "runtime_feature.registered")
              .addKeyValue("feature", name)
              .addKeyValue("consumerClass", state.consumerClass())
              .addKeyValue("actorUserId", actor.userId())
              .log();
      case RuntimeFeatureRegistrationResult.AlreadyExists _ ->
          log.atInfo()
              .setMessage("Runtime feature registration converged on an existing definition")
              .addKeyValue("event", "runtime_feature.registration_noop")
              .addKeyValue("feature", name)
              .addKeyValue("actorUserId", actor.userId())
              .log();
    }
    return new Attempt.Registered(result);
  }
}
