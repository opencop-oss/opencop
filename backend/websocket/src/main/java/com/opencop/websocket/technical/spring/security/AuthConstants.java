// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.security;

public class AuthConstants {
  public static final String ATTR_AUTH = "AUTH";
  public static final String ATTR_EXP = "AUTH_EXP";
  public static final String ATTR_BACKING_SESSION_ID = "BFF_SESSION_ID";
  public static final String ATTR_SESSIONLESS = "SESSIONLESS";
  public static final String DEST_REFRESH = "/app/auth.refresh";
  public static final long EXP_GRACE_SECONDS = 0;
  public static final int AUTH_FAIL = 4400;
  public static final int SESSION_ENDED = 4401;
  public static final int HEARTBEAT_EXPIRED = 4403;
}
