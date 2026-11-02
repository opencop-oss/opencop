// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Core service configuration properties.
 *
 * <pre>
 * opencop:
 *   core:
 *     auto-start-enabled-data-sources-on-startup:
 *       enabled: true                 # optional, default true
 *     assignment-reconciliation:
 *       enabled: true
 *       interval: 15s
 *       initial-delay: 15s
 *     runtime-observation-cleanup:
 *       enabled: true
 *       interval: 1h
 *       initial-delay: 5m
 *       retention: 24h
 *     connect-worker-run-cleanup:
 *       enabled: true
 *       interval: 1h
 *       initial-delay: 5m
 *       retention: 30d
 * </pre>
 */
@ConfigurationProperties(prefix = "opencop.core")
public class CoreProperties {

  private AutoStart autoStartEnabledDataSourcesOnStartup = new AutoStart();
  private AssignmentReconciliation assignmentReconciliation = new AssignmentReconciliation();
  private RuntimeObservationCleanup runtimeObservationCleanup = new RuntimeObservationCleanup();
  private ResourceActivityCleanup resourceActivityCleanup = new ResourceActivityCleanup();
  private ConnectWorkerRunCleanup connectWorkerRunCleanup = new ConnectWorkerRunCleanup();

  public AutoStart getAutoStartEnabledDataSourcesOnStartup() {
    return autoStartEnabledDataSourcesOnStartup;
  }

  public void setAutoStartEnabledDataSourcesOnStartup(
      AutoStart autoStartEnabledDataSourcesOnStartup) {
    this.autoStartEnabledDataSourcesOnStartup = autoStartEnabledDataSourcesOnStartup;
  }

  public AssignmentReconciliation getAssignmentReconciliation() {
    return assignmentReconciliation;
  }

  public void setAssignmentReconciliation(AssignmentReconciliation assignmentReconciliation) {
    this.assignmentReconciliation = assignmentReconciliation;
  }

  public RuntimeObservationCleanup getRuntimeObservationCleanup() {
    return runtimeObservationCleanup;
  }

  public void setRuntimeObservationCleanup(RuntimeObservationCleanup runtimeObservationCleanup) {
    this.runtimeObservationCleanup = runtimeObservationCleanup;
  }

  public ResourceActivityCleanup getResourceActivityCleanup() {
    return resourceActivityCleanup;
  }

  public void setResourceActivityCleanup(ResourceActivityCleanup resourceActivityCleanup) {
    this.resourceActivityCleanup = resourceActivityCleanup;
  }

  public ConnectWorkerRunCleanup getConnectWorkerRunCleanup() {
    return connectWorkerRunCleanup;
  }

  public void setConnectWorkerRunCleanup(ConnectWorkerRunCleanup connectWorkerRunCleanup) {
    this.connectWorkerRunCleanup = connectWorkerRunCleanup;
  }

  /**
   * Configuration for auto-starting enabled data sources on Core startup.
   *
   * <p>When enabled (default), Core reads all enabled data sources from the DB on startup and tells
   * each assigned Connect instance to start them.
   *
   * <p>Set enabled to false when running multiple Core instances to avoid duplicate startup
   * attempts. In a multi-Core deployment, only one instance should have this enabled, OR use an
   * external orchestrator to trigger the process via the admin API.
   */
  public static class AutoStart {

    /** Whether to auto-start enabled data sources on Core startup. */
    private boolean enabled = true;

    /** Expected number of Connect instances that should be registered before auto-start runs. */
    private int expectedConnectInstances = 1;

    /** Max seconds to wait for Connect instances to register before proceeding anyway. */
    private int connectReadyTimeoutSeconds = 60;

    /** Seconds between polls when waiting for Connect instances. */
    private int connectReadyPollIntervalSeconds = 2;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public int getExpectedConnectInstances() {
      return expectedConnectInstances;
    }

    public void setExpectedConnectInstances(int expectedConnectInstances) {
      this.expectedConnectInstances = expectedConnectInstances;
    }

    public int getConnectReadyTimeoutSeconds() {
      return connectReadyTimeoutSeconds;
    }

    public void setConnectReadyTimeoutSeconds(int connectReadyTimeoutSeconds) {
      this.connectReadyTimeoutSeconds = connectReadyTimeoutSeconds;
    }

    public int getConnectReadyPollIntervalSeconds() {
      return connectReadyPollIntervalSeconds;
    }

    public void setConnectReadyPollIntervalSeconds(int connectReadyPollIntervalSeconds) {
      this.connectReadyPollIntervalSeconds = connectReadyPollIntervalSeconds;
    }
  }

  /** Configuration for repairing enabled socket-source ownership after Connect instance churn. */
  public static class AssignmentReconciliation {

    /** Whether Core periodically moves enabled socket sources to route-fresh Connect instances. */
    private boolean enabled = true;

    /** Fixed delay between reconciliation sweeps. */
    private Duration interval = Duration.ofSeconds(15);

    /** Delay before the first sweep after Core starts. */
    private Duration initialDelay = Duration.ofSeconds(15);

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public Duration getInterval() {
      return interval;
    }

    public void setInterval(Duration interval) {
      this.interval = interval;
    }

    public Duration getInitialDelay() {
      return initialDelay;
    }

    public void setInitialDelay(Duration initialDelay) {
      this.initialDelay = initialDelay;
    }
  }

  /** Configuration for deleting old source runtime observations after they expire. */
  /**
   * Configuration for pruning the activity ledger and orphaned projection rows.
   *
   * <p>{@code retention} is a correctness floor rather than a disk-space preference: it must cover
   * the supported replay horizon, which Kafka bounds at 7 days.
   */
  public static class ResourceActivityCleanup {

    private boolean enabled = true;
    private Duration interval = Duration.ofHours(6);
    private Duration initialDelay = Duration.ofMinutes(10);
    private Duration retention = Duration.ofDays(7);

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public Duration getInterval() {
      return interval;
    }

    public void setInterval(Duration interval) {
      this.interval = interval;
    }

    public Duration getInitialDelay() {
      return initialDelay;
    }

    public void setInitialDelay(Duration initialDelay) {
      this.initialDelay = initialDelay;
    }

    public Duration getRetention() {
      return retention;
    }

    public void setRetention(Duration retention) {
      this.retention = retention;
    }
  }

  public static class RuntimeObservationCleanup {

    private boolean enabled = true;
    private Duration interval = Duration.ofHours(1);
    private Duration initialDelay = Duration.ofMinutes(5);
    private Duration retention = Duration.ofHours(24);

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public Duration getInterval() {
      return interval;
    }

    public void setInterval(Duration interval) {
      this.interval = interval;
    }

    public Duration getInitialDelay() {
      return initialDelay;
    }

    public void setInitialDelay(Duration initialDelay) {
      this.initialDelay = initialDelay;
    }

    public Duration getRetention() {
      return retention;
    }

    public void setRetention(Duration retention) {
      this.retention = retention;
    }
  }

  /** Configuration for deleting old stopped or expired Connect worker runs. */
  public static class ConnectWorkerRunCleanup {

    private boolean enabled = true;
    private Duration interval = Duration.ofHours(1);
    private Duration initialDelay = Duration.ofMinutes(5);
    private Duration retention = Duration.ofDays(30);

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public Duration getInterval() {
      return interval;
    }

    public void setInterval(Duration interval) {
      this.interval = interval;
    }

    public Duration getInitialDelay() {
      return initialDelay;
    }

    public void setInitialDelay(Duration initialDelay) {
      this.initialDelay = initialDelay;
    }

    public Duration getRetention() {
      return retention;
    }

    public void setRetention(Duration retention) {
      this.retention = retention;
    }
  }
}
