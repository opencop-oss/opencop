// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket.rtsp;

import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_DATA;
import static org.bytedeco.ffmpeg.global.avutil.AV_LOG_QUIET;
import static org.bytedeco.ffmpeg.global.avutil.av_log_set_level;

import com.opencop.api.connect.technical.config.RtspConnectorDefaults;
import com.opencop.core.sdk.model.connection.spec.transport.RtspConfig;
import java.util.Objects;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.javacv.FFmpegFrameGrabber;

public final class JavaCvRtspKlvExtractor implements RtspKlvExtractor {

  private static final String FFMPEG_CONNECT_TIMEOUT_OPTION = "timeout";
  private static final String FFMPEG_SOCKET_TIMEOUT_OPTION = "stimeout";
  private static final String FFMPEG_READ_WRITE_TIMEOUT_OPTION = "rw_timeout";
  private static final String FFMPEG_RTSP_TRANSPORT_OPTION = "rtsp_transport";
  private static final String FFMPEG_RTSP_TRANSPORT_TCP = "tcp";

  static {
    av_log_set_level(AV_LOG_QUIET);
  }

  private final RtspConfig config;
  private final RtspConnectorDefaults connectorDefaults;
  private final String input;
  private final FrameGrabberFactory grabberFactory;
  private final Object lifecycleLock = new Object();

  private volatile boolean closed;
  private volatile FrameGrabberHandle grabber;

  public JavaCvRtspKlvExtractor(RtspConfig config) {
    this(config, RtspConnectorDefaults.defaults());
  }

  public JavaCvRtspKlvExtractor(RtspConfig config, RtspConnectorDefaults connectorDefaults) {
    this(config.url(), config, connectorDefaults);
  }

  JavaCvRtspKlvExtractor(String input, RtspConfig config) {
    this(input, config, RtspConnectorDefaults.defaults());
  }

  JavaCvRtspKlvExtractor(String input, RtspConfig config, RtspConnectorDefaults connectorDefaults) {
    this(input, config, connectorDefaults, JavaCvFrameGrabberHandle::new);
  }

  JavaCvRtspKlvExtractor(
      String input,
      RtspConfig config,
      RtspConnectorDefaults connectorDefaults,
      FrameGrabberFactory grabberFactory) {
    this.input = Objects.requireNonNull(input, "input");
    this.config = Objects.requireNonNull(config, "config");
    this.connectorDefaults = Objects.requireNonNull(connectorDefaults, "connectorDefaults");
    this.grabberFactory = Objects.requireNonNull(grabberFactory, "grabberFactory");
  }

  @Override
  public void start() throws RtspKlvExtractorException {
    synchronized (lifecycleLock) {
      if (grabber != null || closed) {
        return;
      }
    }

    var newGrabber = grabberFactory.create(input);
    configure(newGrabber);
    try {
      newGrabber.start();
    } catch (Throwable t) {
      closeQuietly(newGrabber);
      throw wrap("Failed to open RTSP KLV stream", t);
    }

    synchronized (lifecycleLock) {
      if (!closed && grabber == null) {
        grabber = newGrabber;
        return;
      }
    }
    closeQuietly(newGrabber);
  }

  @Override
  public byte[] readNextPacket(long deadlineNanos) throws RtspKlvExtractorException {
    if (closed) {
      return null;
    }
    var currentGrabber = grabber;
    if (currentGrabber == null) {
      throw new RtspKlvExtractorException("RTSP KLV extractor has not been started", null);
    }

    while (!closed) {
      if (System.nanoTime() >= deadlineNanos) {
        throw new NoKlvMetadataException(
            "RTSP source did not produce complete KLV metadata before the first metadata deadline");
      }
      AVPacket packet = null;
      try {
        packet = currentGrabber.grabPacket();
        if (packet == null) {
          return null;
        }
        if (!isKlvCandidate(packet, currentGrabber.getFormatContext())) {
          continue;
        }
        return copyPacketBytes(packet);
      } catch (Throwable t) {
        if (closed) {
          return null;
        }
        throw wrap("Failed to read RTSP KLV packet", t);
      } finally {
        if (packet != null) {
          av_packet_unref(packet);
        }
      }
    }
    return null;
  }

  @Override
  public void close() {
    FrameGrabberHandle toClose;
    synchronized (lifecycleLock) {
      if (closed) {
        return;
      }
      closed = true;
      toClose = grabber;
    }
    closeQuietly(toClose);
  }

  private void configure(FrameGrabberHandle target) {
    var connectTimeoutMicros = connectorDefaults.connectTimeoutMicros();
    var readTimeoutMicros = connectorDefaults.readTimeoutMicros();
    target.setTimeout(connectTimeoutMicros);
    target.setOption(FFMPEG_CONNECT_TIMEOUT_OPTION, String.valueOf(connectTimeoutMicros));
    // Retained for older FFmpeg compatibility; current behavior relies on timeout/rw_timeout.
    target.setOption(FFMPEG_SOCKET_TIMEOUT_OPTION, String.valueOf(connectTimeoutMicros));
    target.setOption(FFMPEG_READ_WRITE_TIMEOUT_OPTION, String.valueOf(readTimeoutMicros));
    if (connectorDefaults.preferTcp() && input.startsWith("rtsp")) {
      target.setOption(FFMPEG_RTSP_TRANSPORT_OPTION, FFMPEG_RTSP_TRANSPORT_TCP);
    }
  }

  private static void closeQuietly(FrameGrabberHandle target) {
    if (target == null) {
      return;
    }
    try {
      target.close();
    } catch (Exception _) {
    }
  }

  private static boolean isKlvCandidate(AVPacket packet, AVFormatContext context) {
    if (packet == null || packet.size() <= 0) {
      return false;
    }
    // Packets on a DATA-type stream are always KLV candidates.
    if (streamType(context, packet.stream_index()) == AVMEDIA_TYPE_DATA) {
      return true;
    }
    if (packet.data() == null) {
      return false;
    }
    // For non-DATA streams, accept packets whose payload starts with the full 16-byte
    // universal key (Form 1) or the 11-byte partial key (Form 2 — stripped SMPTE prefix).
    // Some senders or proxies (e.g. MediaMTX) do not advertise the KLV track as AVMEDIA_TYPE_DATA
    // in the SDP, so we fall back to payload-level key detection.
    return packetStartsWith(packet, KlvPacketFramer.ST_0601_UNIVERSAL_KEY)
        || packetStartsWith(packet, KlvPacketFramer.ST_0601_PARTIAL_KEY);
  }

  private static boolean packetStartsWith(AVPacket packet, byte[] key) {
    if (packet.size() < key.length) {
      return false;
    }
    var prefix = new byte[key.length];
    packet.data().position(0).get(prefix);
    for (int i = 0; i < key.length; i++) {
      if (prefix[i] != key[i]) {
        return false;
      }
    }
    return true;
  }

  private static int streamType(AVFormatContext context, int streamIndex) {
    if (context == null || streamIndex < 0 || streamIndex >= context.nb_streams()) {
      return Integer.MIN_VALUE;
    }
    return context.streams(streamIndex).codecpar().codec_type();
  }

  private static byte[] copyPacketBytes(AVPacket packet) {
    var bytes = new byte[Math.max(packet.size(), 0)];
    if (bytes.length > 0 && packet.data() != null) {
      packet.data().position(0).get(bytes);
    }
    return bytes;
  }

  private RtspKlvExtractorException wrap(String prefix, Throwable throwable) {
    return new RtspKlvExtractorException(prefix, RtspUrlRedactor.redactException(throwable));
  }

  @FunctionalInterface
  interface FrameGrabberFactory {
    FrameGrabberHandle create(String input);
  }

  interface FrameGrabberHandle extends AutoCloseable {
    void setTimeout(int timeout);

    void setOption(String key, String value);

    void start() throws Exception;

    AVPacket grabPacket() throws Exception;

    AVFormatContext getFormatContext();

    @Override
    void close() throws Exception;
  }

  private static final class JavaCvFrameGrabberHandle implements FrameGrabberHandle {
    private final FFmpegFrameGrabber delegate;

    private JavaCvFrameGrabberHandle(String input) {
      this.delegate = new FFmpegFrameGrabber(input);
    }

    @Override
    public void setTimeout(int timeout) {
      delegate.setTimeout(timeout);
    }

    @Override
    public void setOption(String key, String value) {
      delegate.setOption(key, value);
    }

    @Override
    public void start() throws Exception {
      delegate.start();
    }

    @Override
    public AVPacket grabPacket() throws Exception {
      return delegate.grabPacket();
    }

    @Override
    public AVFormatContext getFormatContext() {
      return delegate.getFormatContext();
    }

    @Override
    public void close() throws Exception {
      delegate.close();
    }
  }
}
