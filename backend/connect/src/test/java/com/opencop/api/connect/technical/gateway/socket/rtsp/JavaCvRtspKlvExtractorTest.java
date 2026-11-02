// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket.rtsp;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.api.connect.technical.config.RtspConnectorDefaults;
import com.opencop.core.sdk.model.connection.spec.transport.RtspConfig;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.junit.jupiter.api.Test;

class JavaCvRtspKlvExtractorTest {

  @Test
  void configuresFrameGrabberOptionsBeforeStart() throws Exception {
    var grabber = new RecordingStartGrabber();
    var extractor =
        new JavaCvRtspKlvExtractor(
            "rtsp://localhost:8554/klv",
            new RtspConfig("rtsp://localhost:8554/klv", null),
            new RtspConnectorDefaults(Duration.ofMillis(1234), Duration.ofMillis(5678), true),
            _ -> grabber);

    try {
      extractor.start();

      assertThat(grabber.configuredBeforeStart()).isTrue();
      assertThat(grabber.timeout()).isEqualTo(1_234_000);
      assertThat(grabber.options())
          .containsEntry("timeout", "1234000")
          .containsEntry("rw_timeout", "5678000")
          .containsEntry("rtsp_transport", "tcp");
    } finally {
      extractor.close();
    }
  }

  @Test
  void closeDuringStartClosesGrabberAfterStartCompletes() throws Exception {
    var grabber = new BlockingStartGrabber();
    var extractor =
        new JavaCvRtspKlvExtractor(
            "rtsp://localhost:8554/klv",
            new RtspConfig("rtsp://localhost:8554/klv", null),
            new RtspConnectorDefaults(Duration.ofMillis(250), Duration.ofMillis(250), true),
            _ -> grabber);
    var failure = new AtomicReference<Throwable>();
    var startReturned = new CountDownLatch(1);
    var startThread =
        new Thread(
            () -> {
              try {
                extractor.start();
              } catch (Throwable t) {
                failure.set(t);
              } finally {
                startReturned.countDown();
              }
            },
            "JavaCvRtspKlvExtractorTest-start");

    startThread.start();
    try {
      assertThat(grabber.awaitStartEntered()).isTrue();

      extractor.close();

      assertThat(grabber.closeCount()).isZero();

      grabber.finishStart();

      assertThat(startReturned.await(1, TimeUnit.SECONDS)).isTrue();
      assertThat(failure.get()).isNull();
      assertThat(grabber.closeCount()).isEqualTo(1);
      assertThat(extractor.readNextPacket(Long.MAX_VALUE)).isNull();
    } finally {
      grabber.finishStart();
      startThread.join(TimeUnit.SECONDS.toMillis(1));
    }
  }

  private static final class RecordingStartGrabber
      implements JavaCvRtspKlvExtractor.FrameGrabberHandle {
    private final Map<String, String> options = new LinkedHashMap<>();
    private int timeout;
    private boolean configuredBeforeStart;

    @Override
    public void setTimeout(int timeout) {
      this.timeout = timeout;
    }

    @Override
    public void setOption(String key, String value) {
      options.put(key, value);
    }

    @Override
    public void start() {
      configuredBeforeStart =
          timeout > 0
              && options.containsKey("timeout")
              && options.containsKey("rw_timeout")
              && "tcp".equals(options.get("rtsp_transport"));
    }

    @Override
    public AVPacket grabPacket() {
      return null;
    }

    @Override
    public AVFormatContext getFormatContext() {
      return null;
    }

    @Override
    public void close() {}

    int timeout() {
      return timeout;
    }

    Map<String, String> options() {
      return options;
    }

    boolean configuredBeforeStart() {
      return configuredBeforeStart;
    }
  }

  private static final class BlockingStartGrabber
      implements JavaCvRtspKlvExtractor.FrameGrabberHandle {
    private final CountDownLatch startEntered = new CountDownLatch(1);
    private final CountDownLatch finishStart = new CountDownLatch(1);
    private final AtomicInteger closeCount = new AtomicInteger();

    @Override
    public void setTimeout(int timeout) {}

    @Override
    public void setOption(String key, String value) {}

    @Override
    public void start() throws Exception {
      startEntered.countDown();
      if (!finishStart.await(1, TimeUnit.SECONDS)) {
        throw new AssertionError("start was not released");
      }
    }

    @Override
    public AVPacket grabPacket() {
      return null;
    }

    @Override
    public AVFormatContext getFormatContext() {
      return null;
    }

    @Override
    public void close() {
      closeCount.incrementAndGet();
    }

    boolean awaitStartEntered() throws InterruptedException {
      return startEntered.await(1, TimeUnit.SECONDS);
    }

    void finishStart() {
      finishStart.countDown();
    }

    int closeCount() {
      return closeCount.get();
    }
  }
}
