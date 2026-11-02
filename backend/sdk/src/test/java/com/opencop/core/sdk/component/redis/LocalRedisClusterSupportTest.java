// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.component.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.InspectExecCmd;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

class LocalRedisClusterSupportTest {

  private final GenericContainer<?> container = mock(GenericContainer.class);
  private final DockerClient client = mock(DockerClient.class);
  private final ExecCreateCmd create = mock(ExecCreateCmd.class, RETURNS_SELF);
  private final ExecStartCmd start = mock(ExecStartCmd.class);
  private final Closeable stream = mock(Closeable.class);

  @BeforeEach
  void configureExec() {
    when(container.getDockerClient()).thenReturn(client);
    when(container.getContainerId()).thenReturn("owned-container");
    when(client.execCreateCmd("owned-container")).thenReturn(create);
    var created = mock(ExecCreateCmdResponse.class);
    when(create.exec()).thenReturn(created);
    when(created.getId()).thenReturn("owned-exec");
    when(client.execStartCmd("owned-exec")).thenReturn(start);
  }

  @AfterEach
  void clearInterrupt() {
    Thread.interrupted();
  }

  @Test
  void checkedExec_success_preservesUtf8StdoutAndClosesStream() throws Exception {
    completesWith(0, "event 台\n", "diagnostic\n");

    assertThat(exec(Duration.ofSeconds(5))).isEqualTo("event 台\n");

    verify(create).withAttachStdout(true);
    verify(create).withAttachStderr(true);
    verify(create).withCmd("redis-cli", "PING");
    verify(stream, atLeastOnce()).close();
  }

  @Test
  void checkedExec_nonzeroExit_reportsStderrAndClosesStream() throws Exception {
    completesWith(7, "partial output", "connection failed\n");

    assertThatThrownBy(() -> exec(Duration.ofSeconds(5)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("redis-cli PING")
        .hasMessageContaining("stderr=connection failed");

    verify(stream, atLeastOnce()).close();
  }

  @Test
  void checkedExec_noCompletion_timesOutAndClosesWithoutInspectingExec() throws Exception {
    doAnswer(
            invocation -> {
              ResultCallback<Frame> callback = invocation.getArgument(0);
              callback.onStart(stream);
              return callback;
            })
        .when(start)
        .exec(any());

    assertThatThrownBy(() -> exec(Duration.ofMillis(100)))
        .isInstanceOf(LocalRedisClusterSupport.ExecTimeoutException.class)
        .hasMessageContaining("redis-cli PING");

    verify(stream, atLeastOnce()).close();
    verify(client, never()).inspectExecCmd(any());
  }

  @Test
  void checkedExec_interrupted_preservesInterruptAndClosesWithoutInspectingExec() throws Exception {
    doAnswer(
            invocation -> {
              ResultCallback<Frame> callback = invocation.getArgument(0);
              callback.onStart(stream);
              Thread.currentThread().interrupt();
              return callback;
            })
        .when(start)
        .exec(any());

    assertThatThrownBy(() -> exec(Duration.ofSeconds(5)))
        .isInstanceOf(IllegalStateException.class)
        .hasCauseInstanceOf(InterruptedException.class);

    assertThat(Thread.currentThread().isInterrupted()).isTrue();
    verify(stream, atLeastOnce()).close();
    verify(client, never()).inspectExecCmd(any());
  }

  @Test
  void checkedExec_asyncFailure_preservesFailureAndClosesWithoutInspectingExec() throws Exception {
    var failure = new IllegalStateException("broken exec stream");
    doAnswer(
            invocation -> {
              ResultCallback<Frame> callback = invocation.getArgument(0);
              callback.onStart(stream);
              callback.onError(failure);
              return callback;
            })
        .when(start)
        .exec(any());

    assertThatThrownBy(() -> exec(Duration.ofSeconds(5))).isSameAs(failure);

    verify(stream, atLeastOnce()).close();
    verify(client, never()).inspectExecCmd(any());
  }

  @Test
  void checkedExec_expiredBudget_doesNotStartAnotherCommand() {
    assertThatThrownBy(
            () ->
                LocalRedisClusterSupport.checkedExec(
                    container, System.nanoTime() - 1, "redis-cli", "PING"))
        .isInstanceOf(LocalRedisClusterSupport.ExecTimeoutException.class);

    verifyNoInteractions(container, client, create, start, stream);
  }

  private String exec(Duration timeout) {
    return LocalRedisClusterSupport.checkedExec(
        container, System.nanoTime() + timeout.toNanos(), "redis-cli", "PING");
  }

  private void completesWith(long exitCode, String stdout, String stderr) {
    doAnswer(
            invocation -> {
              ResultCallback<Frame> callback = invocation.getArgument(0);
              callback.onStart(stream);
              callback.onNext(
                  new Frame(StreamType.STDOUT, stdout.getBytes(StandardCharsets.UTF_8)));
              callback.onNext(
                  new Frame(StreamType.STDERR, stderr.getBytes(StandardCharsets.UTF_8)));
              callback.onComplete();
              return callback;
            })
        .when(start)
        .exec(any());
    var inspect = mock(InspectExecCmd.class);
    var inspected = mock(InspectExecResponse.class);
    when(client.inspectExecCmd("owned-exec")).thenReturn(inspect);
    when(inspect.exec()).thenReturn(inspected);
    when(inspected.getExitCodeLong()).thenReturn(exitCode);
  }
}
