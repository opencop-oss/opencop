// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.audit;

public final class AuthzAuditRequestAttributes {

  public static final String REPORTED = "com.opencop.authz.audit.reported";
  public static final String CANDIDATE_REASON = "com.opencop.authz.audit.candidate_reason";
  public static final String PRINCIPAL_TYPE = "com.opencop.authz.audit.principal_type";
  public static final String ACTOR_USER_ID = "com.opencop.authz.audit.actor_user_id";
  public static final String ACTOR_EMAIL = "com.opencop.authz.audit.actor_email";

  private AuthzAuditRequestAttributes() {}
}
