// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.model.opencop;

public class Timestampz {

  // epoch millis
  private long time;
  private long start;
  private long stale;

  public Timestampz() {}

  public Timestampz(long time, long start, long stale) {
    this.time = time;
    this.start = start;
    this.stale = stale;
  }

  public long getTime() {
    return time;
  }

  public void setTime(long time) {
    this.time = time;
  }

  public long getStart() {
    return start;
  }

  public void setStart(long start) {
    this.start = start;
  }

  public long getStale() {
    return stale;
  }

  public void setStale(long stale) {
    this.stale = stale;
  }

  public static class Builder {
    private Long time, start, stale;

    public Builder time(long v) {
      this.time = v;
      return this;
    }

    public Builder start(long v) {
      this.start = v;
      return this;
    }

    public Builder stale(long v) {
      this.stale = v;
      return this;
    }

    public Timestampz build() {
      return new Timestampz(
          time != null ? time : 0L, start != null ? start : 0L, stale != null ? stale : 0L);
    }
  }
}
