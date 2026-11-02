// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.profile;

import com.opencop.core.api.general.business.model.profile.MapperCatalogProfile;
import com.opencop.core.api.general.ports.gateway.profile.MapperCatalogGateway;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaMapperCatalogGateway implements MapperCatalogGateway {

  private final EntityManager entityManager;

  public JpaMapperCatalogGateway(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public List<MapperCatalogProfile> loadCatalog() {
    // The writes below are bulk updates, which bypass the persistence context: without a clear, a
    // second load in the same transaction would return the entities as they were first read.
    entityManager.flush();
    entityManager.clear();
    return entityManager
        .createQuery(
            """
            SELECT new %s(mapper, identity)
              FROM MapperCatalogEntry mapper
              JOIN MapperIdentity identity ON identity.id = mapper.mapperId
            """
                .formatted(MapperCatalogRow.class.getName()),
            MapperCatalogRow.class)
        .getResultList()
        .stream()
        .map(row -> MapperCatalogProfileFactory.create(row.mapper(), row.identity()))
        .toList();
  }

  @Override
  public int updateMetadata(UUID mapperId, String name, String description) {
    return entityManager
        .createQuery(
            """
            UPDATE MapperCatalogEntry mapper
               SET mapper.name = :name,
                   mapper.description = :description
             WHERE mapper.mapperId = :mapperId
            """)
        .setParameter("mapperId", mapperId)
        .setParameter("name", name)
        .setParameter("description", description)
        .executeUpdate();
  }

  @Override
  public int reconcileAvailability(UUID mapperId, boolean archived) {
    return entityManager
        .createQuery(
            """
            UPDATE MapperCatalogEntry mapper
               SET mapper.archived = :archived
             WHERE mapper.mapperId = :mapperId
               AND mapper.archived <> :archived
               AND EXISTS (
                 SELECT identity.id
                   FROM MapperIdentity identity
                  WHERE identity.id = mapper.mapperId
                    AND identity.integrationId IS NOT NULL)
            """)
        .setParameter("mapperId", mapperId)
        .setParameter("archived", archived)
        .executeUpdate();
  }

  /**
   * A lock taken outside the caller's transaction would end at once, so one is required. Every
   * locker takes the rows in the same order, so two creations never wait on each other in a cycle
   * once an archive holds one of their mappers.
   */
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Set<UUID> lockAndFindArchived(Collection<UUID> mapperIds) {
    var ordered = new TreeSet<>(mapperIds);
    if (ordered.isEmpty()) {
      return Set.of();
    }
    // One identity row per mapper, whatever its number of revisions, so one lock covers them all.
    for (var mapperId : ordered) {
      entityManager.find(MapperIdentityEntity.class, mapperId, LockModeType.PESSIMISTIC_READ);
    }
    // Its own statement: in READ COMMITTED only a statement that starts once the locks are granted
    // sees an archive that committed while this waited for them. JPQL rather than a derived query,
    // which cannot express picking each mapper's latest revision with a correlated subquery.
    return Set.copyOf(
        entityManager
            .createQuery(
                """
                SELECT mapper.mapperId
                  FROM MapperCatalogEntry mapper
                 WHERE mapper.mapperId IN :ids
                   AND mapper.archived = true
                   AND mapper.version = (
                     SELECT MAX(latest.version)
                       FROM MapperCatalogEntry latest
                      WHERE latest.mapperId = mapper.mapperId)
                """,
                UUID.class)
            .setParameter("ids", ordered)
            .getResultList());
  }

  @Override
  public int assignIntegration(UUID mapperId, UUID integrationId) {
    return entityManager
        .createQuery(
            """
            UPDATE MapperIdentity identity
               SET identity.integrationId = :integrationId
             WHERE identity.id = :mapperId
            """)
        .setParameter("mapperId", mapperId)
        .setParameter("integrationId", integrationId)
        .executeUpdate();
  }
}
