// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.export;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "export_artifact", schema = "app")
public class ExportArtifactEntity {

  @Id
  @Column(name = "export_id", nullable = false, updatable = false)
  private UUID exportId;

  @Column(name = "subject_type", nullable = false)
  private String subjectType;

  @Column(name = "subject_id", nullable = false)
  private UUID subjectId;

  @Column(name = "creator_id", nullable = false)
  private UUID creatorId;

  @Column(name = "format", nullable = false)
  private String format;

  @Column(name = "blob_path", nullable = false)
  private String blobPath;

  @Column(name = "filename", nullable = false)
  private String filename;

  @Column(name = "generated_at", nullable = false)
  private OffsetDateTime generatedAt;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  public UUID getExportId() {
    return exportId;
  }

  public void setExportId(UUID exportId) {
    this.exportId = exportId;
  }

  public String getSubjectType() {
    return subjectType;
  }

  public void setSubjectType(String subjectType) {
    this.subjectType = subjectType;
  }

  public UUID getSubjectId() {
    return subjectId;
  }

  public void setSubjectId(UUID subjectId) {
    this.subjectId = subjectId;
  }

  public UUID getCreatorId() {
    return creatorId;
  }

  public void setCreatorId(UUID creatorId) {
    this.creatorId = creatorId;
  }

  public String getFormat() {
    return format;
  }

  public void setFormat(String format) {
    this.format = format;
  }

  public String getBlobPath() {
    return blobPath;
  }

  public void setBlobPath(String blobPath) {
    this.blobPath = blobPath;
  }

  public String getFilename() {
    return filename;
  }

  public void setFilename(String filename) {
    this.filename = filename;
  }

  public OffsetDateTime getGeneratedAt() {
    return generatedAt;
  }

  public void setGeneratedAt(OffsetDateTime generatedAt) {
    this.generatedAt = generatedAt;
  }

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(OffsetDateTime expiresAt) {
    this.expiresAt = expiresAt;
  }
}
