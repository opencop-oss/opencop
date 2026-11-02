// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.technical.gateway.history;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

@Component
public class CoreHistoricalRedisScripts {

  private static final String CANCEL_JOB_IF_ACTIVE_AND_OWNED_SCRIPT_PATH =
      "redis/history/cancel_job_if_active_and_owned.lua";

  private final RedisScript<Long> cancelJobIfActiveAndOwned;

  public CoreHistoricalRedisScripts() {
    this.cancelJobIfActiveAndOwned = longScript(CANCEL_JOB_IF_ACTIVE_AND_OWNED_SCRIPT_PATH);
  }

  public RedisScript<Long> cancelJobIfActiveAndOwned() {
    return cancelJobIfActiveAndOwned;
  }

  private static RedisScript<Long> longScript(String path) {
    var script = new DefaultRedisScript<Long>();
    script.setResultType(Long.class);
    script.setScriptSource(new ResourceScriptSource(new ClassPathResource(path)));
    return script;
  }
}
