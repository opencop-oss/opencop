// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.userdirectory;

import com.opencop.core.sdk.model.iam.userdirectory.UserDirectoryEntryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

@Entity
@Immutable
@Subselect(
    """
    SELECT u.id AS id,
           'USER' AS entry_type,
           u.username AS name,
           u.first_name AS first_name,
           u.last_name AS last_name,
           u.email AS email,
           u.display_name AS display_name,
           u.administrator AS admin,
           u.active AS active,
           u.created_at AS created_at,
           u.updated_at AS updated_at
    FROM opencop_user u
    WHERE u.deleted_at IS NULL
    UNION ALL
    SELECT g.id AS id,
           'GROUP' AS entry_type,
           g.name AS name,
           NULL AS first_name,
           NULL AS last_name,
           NULL AS email,
           NULL AS display_name,
           NULL AS admin,
           TRUE AS active,
           g.created_at AS created_at,
           NULL AS updated_at
    FROM iam.user_group g
    """)
@Synchronize({"opencop_user", "iam.user_group"})
public class UserDirectoryEntryEntity {

  @Id
  @Column(name = "id")
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "entry_type")
  private UserDirectoryEntryType type;

  @Column(name = "name")
  private String name;

  @Column(name = "first_name")
  private String firstName;

  @Column(name = "last_name")
  private String lastName;

  @Column(name = "email")
  private String email;

  @Column(name = "display_name")
  private String displayName;

  @Column(name = "admin")
  private Boolean admin;

  @Column(name = "active")
  private boolean active;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  public UUID getId() {
    return id;
  }

  public UserDirectoryEntryType getType() {
    return type;
  }

  public String getName() {
    return name;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public String getEmail() {
    return email;
  }

  public String getDisplayName() {
    return displayName;
  }

  public Boolean getAdmin() {
    return admin;
  }

  public boolean isActive() {
    return active;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
