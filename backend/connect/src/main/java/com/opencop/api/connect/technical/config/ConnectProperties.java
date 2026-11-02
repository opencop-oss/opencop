// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Spring configuration properties for the Connect service.
 *
 * <p>Binds to application.yml section:
 *
 * <pre>
 * opencop:
 *   connect:
 *     socket:
 *       read-timeout-seconds: 60
 *       tcp-keepalive: true
 * </pre>
 *
 * <p>Instance identity is resolved at runtime via {@link
 * com.opencop.core.sdk.helper.InstanceIdentity#resolveInstanceId(String)}.
 */
@Component
@ConfigurationProperties(prefix = "opencop.connect")
public class ConnectProperties {

  private Socket socket = new Socket();
  private Reconciliation reconciliation = new Reconciliation();
  private PeriodicDataSourceRestart periodicDataSourceRestart = new PeriodicDataSourceRestart();
  private Runtime runtime = new Runtime();
  private DesiredState desiredState = new DesiredState();
  private PostgresListener postgresListener = new PostgresListener();
  private RtspKlv rtspKlv = new RtspKlv();

  public Socket getSocket() {
    return socket;
  }

  public void setSocket(Socket socket) {
    this.socket = socket;
  }

  public Reconciliation getReconciliation() {
    return reconciliation;
  }

  public void setReconciliation(Reconciliation reconciliation) {
    this.reconciliation = reconciliation;
  }

  public PeriodicDataSourceRestart getPeriodicDataSourceRestart() {
    return periodicDataSourceRestart;
  }

  public void setPeriodicDataSourceRestart(PeriodicDataSourceRestart periodicDataSourceRestart) {
    this.periodicDataSourceRestart = periodicDataSourceRestart;
  }

  public Runtime getRuntime() {
    return runtime;
  }

  public void setRuntime(Runtime runtime) {
    this.runtime = runtime;
  }

  public DesiredState getDesiredState() {
    return desiredState;
  }

  public void setDesiredState(DesiredState desiredState) {
    this.desiredState = desiredState;
  }

  public PostgresListener getPostgresListener() {
    return postgresListener;
  }

  public void setPostgresListener(PostgresListener postgresListener) {
    this.postgresListener = postgresListener;
  }

  public RtspKlv getRtspKlv() {
    return rtspKlv;
  }

  public void setRtspKlv(RtspKlv rtspKlv) {
    this.rtspKlv = rtspKlv;
  }

  public static class Reconciliation {
    private boolean enabled = true;
    private Duration interval = Duration.ofSeconds(10);
    private Duration initialDelay = Duration.ZERO;
    private Duration recoveryMinInterval = Duration.ofSeconds(180);

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

    public Duration getRecoveryMinInterval() {
      return recoveryMinInterval;
    }

    public void setRecoveryMinInterval(Duration recoveryMinInterval) {
      this.recoveryMinInterval = recoveryMinInterval;
    }
  }

  public static class PeriodicDataSourceRestart {
    private boolean enabled = true;
    private Duration interval = Duration.ofHours(12);
    private Duration initialDelay = Duration.ofHours(12);

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

  public static class Runtime {
    private Duration statusTtl = Duration.ofSeconds(90);

    public Duration getStatusTtl() {
      return statusTtl;
    }

    public void setStatusTtl(Duration statusTtl) {
      this.statusTtl = statusTtl;
    }
  }

  public static class DesiredState {
    private boolean stopOnUnavailable = false;
    private Duration unavailableGrace = Duration.ofSeconds(60);

    public boolean isStopOnUnavailable() {
      return stopOnUnavailable;
    }

    public void setStopOnUnavailable(boolean stopOnUnavailable) {
      this.stopOnUnavailable = stopOnUnavailable;
    }

    public Duration getUnavailableGrace() {
      return unavailableGrace;
    }

    public void setUnavailableGrace(Duration unavailableGrace) {
      this.unavailableGrace = unavailableGrace;
    }
  }

  public static class PostgresListener {
    private DataSource dataSource = new DataSource();
    private Shutdown shutdown = new Shutdown();

    public DataSource getDataSource() {
      return dataSource;
    }

    public void setDataSource(DataSource dataSource) {
      this.dataSource = dataSource;
    }

    public Shutdown getShutdown() {
      return shutdown;
    }

    public void setShutdown(Shutdown shutdown) {
      this.shutdown = shutdown;
    }

    public static class DataSource {
      private int maximumPoolSize = 1;
      private int minimumIdle = 1;
      private Duration connectionTimeout = Duration.ofSeconds(10);
      private String poolName = "opencop-connect-postgres-listener";

      public int getMaximumPoolSize() {
        return maximumPoolSize;
      }

      public void setMaximumPoolSize(int maximumPoolSize) {
        this.maximumPoolSize = maximumPoolSize;
      }

      public int getMinimumIdle() {
        return minimumIdle;
      }

      public void setMinimumIdle(int minimumIdle) {
        this.minimumIdle = minimumIdle;
      }

      public Duration getConnectionTimeout() {
        return connectionTimeout;
      }

      public void setConnectionTimeout(Duration connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
      }

      public String getPoolName() {
        return poolName;
      }

      public void setPoolName(String poolName) {
        this.poolName = poolName;
      }
    }

    public static class Shutdown {
      private Duration joinTimeout = Duration.ofSeconds(5);

      public Duration getJoinTimeout() {
        return joinTimeout;
      }

      public void setJoinTimeout(Duration joinTimeout) {
        this.joinTimeout = joinTimeout;
      }
    }
  }

  public static class RtspKlv {
    private Duration connectTimeout = RtspConnectorDefaults.DEFAULT_CONNECT_TIMEOUT;
    private Duration readTimeout = RtspConnectorDefaults.DEFAULT_READ_TIMEOUT;
    private boolean preferTcp = RtspConnectorDefaults.DEFAULT_PREFER_TCP;

    /**
     * Maximum time to wait for the first complete KLV metadata unit after an RTSP session opens.
     *
     * <p>This is a Connect policy guardrail, not a per-connection user setting. If the stream does
     * not produce KLV within this window, Connect marks it INCOMPATIBLE instead of retrying
     * indefinitely.
     */
    private Duration firstMetadataTimeout = Duration.ofSeconds(120);

    public Duration getConnectTimeout() {
      return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
      this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
      return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
      this.readTimeout = readTimeout;
    }

    public boolean isPreferTcp() {
      return preferTcp;
    }

    public void setPreferTcp(boolean preferTcp) {
      this.preferTcp = preferTcp;
    }

    public Duration getFirstMetadataTimeout() {
      return firstMetadataTimeout;
    }

    public void setFirstMetadataTimeout(Duration firstMetadataTimeout) {
      this.firstMetadataTimeout = firstMetadataTimeout;
    }

    public RtspConnectorDefaults connectorDefaults() {
      return new RtspConnectorDefaults(connectTimeout, readTimeout, preferTcp);
    }
  }

  /**
   * Socket configuration for TCP connections.
   *
   * <p>These defaults apply to all connections unless overridden per-connection.
   */
  public static class Socket {
    /**
     * Read timeout in seconds. When no data is received within this period, a
     * SocketTimeoutException is thrown but the socket remains valid - the read loop continues. This
     * prevents connections from appearing "stuck" when the remote server is quiet or the host
     * machine sleeps.
     *
     * <p>Receive-idle behaviors (such as an integration's ping or idle reconnect) check idle time
     * on this tick, so keep it at or below their idle thresholds.
     *
     * <p>Set to 0 to disable (infinite timeout - not recommended for low-activity connections).
     * Default: 10 seconds (lowered from 60 to support receive-idle granularity).
     */
    private int readTimeoutSeconds = 10;

    /**
     * Enable TCP keepalive to detect dead connections at the OS level. Recommended to leave
     * enabled. Default: true.
     */
    private boolean tcpKeepalive = true;

    /**
     * Maximum message size in bytes. Messages exceeding this size will be dropped. Applies to both
     * delimiter-framed and length-prefixed protocols.
     *
     * <p>Default: 1_048_576 (1 MB).
     */
    private int maxMessageSizeBytes = 1_024 * 1_024;

    public int getReadTimeoutSeconds() {
      return readTimeoutSeconds;
    }

    public void setReadTimeoutSeconds(int readTimeoutSeconds) {
      this.readTimeoutSeconds = readTimeoutSeconds;
    }

    public boolean isTcpKeepalive() {
      return tcpKeepalive;
    }

    public void setTcpKeepalive(boolean tcpKeepalive) {
      this.tcpKeepalive = tcpKeepalive;
    }

    public int getMaxMessageSizeBytes() {
      return maxMessageSizeBytes;
    }

    public void setMaxMessageSizeBytes(int maxMessageSizeBytes) {
      this.maxMessageSizeBytes = maxMessageSizeBytes;
    }

    /** Get read timeout in milliseconds (for socket.setSoTimeout). */
    public int getReadTimeoutMs() {
      return readTimeoutSeconds * 1000;
    }
  }
}
