// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.media;

/** Transport of a URL media attachment; selects the player the client constructs. */
public enum StreamKind {
  /** rtsp:// or rtsps:// — converted to HLS on demand by MediaMTX. */
  RTSP,
  /** An HLS manifest served directly; no conversion needed. */
  HLS,
  /** An HTTP MJPEG feed (multipart/x-mixed-replace) rendered by the browser in an {@code <img>}. */
  MJPEG,
  /**
   * An HTTP MPEG transport stream (video/mp2t) played directly by the browser, with no conversion.
   */
  MPEGTS,
  /** srt:// caller URL — converted to HLS on demand by MediaMTX, like RTSP. */
  SRT
}
