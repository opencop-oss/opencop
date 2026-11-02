// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.dlt;

final class DeadLetterAdminContextSchema {

  static final String SCHEMA = "processor";
  static final String FAILURE_ID_COLUMN = "failure_id";
  static final String RECORD_TABLE = "processor_dead_letter_record";
  static final String QUALIFIED_RECORD_TABLE = SCHEMA + "." + RECORD_TABLE;
  static final String SOURCE_CONTEXT_TABLE = "processor_dead_letter_source_context";
  static final String JOB_CONTEXT_TABLE = "processor_dead_letter_job_context";
  static final String MAPPER_CONTEXT_TABLE = "processor_dead_letter_mapper_context";
  static final String IMPORT_CONTEXT_TABLE = "processor_dead_letter_import_context";
  static final String QUALIFIED_SOURCE_CONTEXT_TABLE = SCHEMA + "." + SOURCE_CONTEXT_TABLE;
  static final String QUALIFIED_JOB_CONTEXT_TABLE = SCHEMA + "." + JOB_CONTEXT_TABLE;
  static final String QUALIFIED_MAPPER_CONTEXT_TABLE = SCHEMA + "." + MAPPER_CONTEXT_TABLE;
  static final String QUALIFIED_IMPORT_CONTEXT_TABLE = SCHEMA + "." + IMPORT_CONTEXT_TABLE;
  static final String SOURCE_ID_COLUMN = "source_id";
  static final String JOB_ID_COLUMN = "job_id";
  static final String MAPPER_ID_COLUMN = "mapper_id";
  static final String IMPORT_ID_COLUMN = "import_id";

  private DeadLetterAdminContextSchema() {}
}
