// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.media;

public enum StreamableAttachmentSourceType {
  /** An {@code rtsp://} or {@code rtsps://} URL pulled by MediaMTX. */
  RTSP_URL,
  /** An {@code srt://} caller URL pulled by MediaMTX. */
  SRT_URL
}
