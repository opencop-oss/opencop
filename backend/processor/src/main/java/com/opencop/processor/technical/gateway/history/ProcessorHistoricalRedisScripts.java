// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

@Component
public class ProcessorHistoricalRedisScripts {

  private static final String CREATE_RECEIVED_IF_ABSENT_SCRIPT_PATH =
      "redis/history/job_create_received_if_absent.lua";
  private static final String UPDATE_WITH_GUARDS_SCRIPT_PATH =
      "redis/history/job_update_with_guards.lua";
  private static final String MARK_CANCELLATION_REQUESTED_SCRIPT_PATH =
      "redis/history/job_mark_cancellation_requested.lua";
  private static final String REFRESH_ACTIVE_TTL_SCRIPT_PATH =
      "redis/history/job_refresh_active_ttl.lua";
  private static final String REGISTER_SCRIPT_PATH = "redis/history/admission_register.lua";
  private static final String RECORD_EXECUTION_EXIT_SCRIPT_PATH =
      "redis/history/admission_record_execution_exit.lua";
  private static final String RECORD_TERMINAL_SCRIPT_PATH =
      "redis/history/admission_record_terminal.lua";
  private static final String CLEANUP_ADMISSION_STATE_SCRIPT_PATH =
      "redis/history/admission_cleanup_state.lua";
  private static final String REGISTER_CLUSTER_SCRIPT_PATH =
      "redis/history/admission_register_cluster.lua";
  private static final String CLEANUP_ADMISSION_STATE_CLUSTER_SCRIPT_PATH =
      "redis/history/admission_cleanup_state_cluster.lua";
  private static final String READ_QUEUED_PAYLOAD_CLUSTER_SCRIPT_PATH =
      "redis/history/admission_read_queued_payload_cluster.lua";

  private final RedisScript<Long> createReceivedIfAbsent;
  private final RedisScript<Long> updateWithGuards;
  private final RedisScript<Long> markCancellationRequested;
  private final RedisScript<Long> refreshActiveTtl;
  private final RedisScript<List<Object>> admissionRegister;
  private final RedisScript<List<Object>> admissionRecordExecutionExit;
  private final RedisScript<List<Object>> admissionRecordTerminal;
  private final RedisScript<String> admissionCleanupState;
  private final RedisScript<List<Object>> admissionRegisterCluster;
  private final RedisScript<String> admissionCleanupStateCluster;
  private final RedisScript<String> admissionReadQueuedPayloadCluster;

  public ProcessorHistoricalRedisScripts() {
    this.createReceivedIfAbsent = script(CREATE_RECEIVED_IF_ABSENT_SCRIPT_PATH, Long.class);
    this.updateWithGuards = script(UPDATE_WITH_GUARDS_SCRIPT_PATH, Long.class);
    this.markCancellationRequested = script(MARK_CANCELLATION_REQUESTED_SCRIPT_PATH, Long.class);
    this.refreshActiveTtl = script(REFRESH_ACTIVE_TTL_SCRIPT_PATH, Long.class);
    this.admissionRegister = listScript(REGISTER_SCRIPT_PATH);
    this.admissionRecordExecutionExit = listScript(RECORD_EXECUTION_EXIT_SCRIPT_PATH);
    this.admissionRecordTerminal = listScript(RECORD_TERMINAL_SCRIPT_PATH);
    this.admissionCleanupState = script(CLEANUP_ADMISSION_STATE_SCRIPT_PATH, String.class);
    this.admissionRegisterCluster = listScript(REGISTER_CLUSTER_SCRIPT_PATH);
    this.admissionCleanupStateCluster =
        script(CLEANUP_ADMISSION_STATE_CLUSTER_SCRIPT_PATH, String.class);
    this.admissionReadQueuedPayloadCluster =
        script(READ_QUEUED_PAYLOAD_CLUSTER_SCRIPT_PATH, String.class);
  }

  public RedisScript<Long> createReceivedIfAbsent() {
    return createReceivedIfAbsent;
  }

  public RedisScript<Long> updateWithGuards() {
    return updateWithGuards;
  }

  public RedisScript<Long> markCancellationRequested() {
    return markCancellationRequested;
  }

  public RedisScript<Long> refreshActiveTtl() {
    return refreshActiveTtl;
  }

  public RedisScript<List<Object>> admissionRegister() {
    return admissionRegister;
  }

  public RedisScript<List<Object>> admissionRecordExecutionExit() {
    return admissionRecordExecutionExit;
  }

  public RedisScript<List<Object>> admissionRecordTerminal() {
    return admissionRecordTerminal;
  }

  public RedisScript<String> admissionCleanupState() {
    return admissionCleanupState;
  }

  public RedisScript<List<Object>> admissionRegisterCluster() {
    return admissionRegisterCluster;
  }

  public RedisScript<String> admissionCleanupStateCluster() {
    return admissionCleanupStateCluster;
  }

  public RedisScript<String> admissionReadQueuedPayloadCluster() {
    return admissionReadQueuedPayloadCluster;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static RedisScript<List<Object>> listScript(String path) {
    return script(path, (Class) List.class);
  }

  private static <T> RedisScript<T> script(String path, Class<T> resultType) {
    var script = new DefaultRedisScript<T>();
    script.setResultType(resultType);
    script.setScriptSource(new ResourceScriptSource(new ClassPathResource(path)));
    return script;
  }
}
