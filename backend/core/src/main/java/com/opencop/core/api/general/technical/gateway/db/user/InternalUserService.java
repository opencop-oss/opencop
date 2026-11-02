// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.user;

import com.opencop.core.api.general.business.model.user.InternalUser;
import com.opencop.core.api.general.ports.technical.gateway.UserService;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserRepository;
import com.opencop.core.sdk.model.iam.user.UserReadFilter;
import com.opencop.core.sdk.ports.gateway.idp.IdpException;
import com.opencop.core.sdk.ports.gateway.idp.IdpExceptionCodes;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import com.opencop.core.sdk.technical.gateway.db.Pageables;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InternalUserService implements UserService {

  private final InternalUserRepository repo;

  public InternalUserService(InternalUserRepository repo) {
    this.repo = repo;
  }

  @Override
  public boolean exists(UUID id) {
    return repo.existsById(id);
  }

  @Transactional(readOnly = true)
  public InternalUser get(UUID id) throws IdpException {
    var entity = repo.findById(id).orElseThrow(() -> new IdpException(IdpExceptionCodes.NOT_FOUND));
    return InternalUserMapper.fromEntity(entity);
  }

  @Transactional(readOnly = true)
  public InternalUser get(String idpUserId, String issuer) throws IdpException {
    var entity =
        repo.findByIdpIssuerAndIdpUserId(issuer, idpUserId)
            .orElseThrow(() -> new IdpException(IdpExceptionCodes.NOT_FOUND));
    return InternalUserMapper.fromEntity(entity);
  }

  @Override
  @Transactional(readOnly = true)
  public PageResponse<InternalUser> getPage(UserReadFilter filter, Pageable pageable) {
    Pageable ordered =
        Pageables.withStableOrder(
            UserSearchSort.validate(pageable),
            Sort.by(Sort.Order.asc("username"), Sort.Order.asc("id")),
            "id");
    Page<InternalUserEntity> page = repo.findAll(UserSpecifications.from(filter), ordered);
    return new PageResponse<>(
        page.getContent().stream().map(InternalUserMapper::fromEntity).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }
}
