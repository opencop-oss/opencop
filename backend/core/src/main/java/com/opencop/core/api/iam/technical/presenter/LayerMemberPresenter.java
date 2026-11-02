// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.technical.presenter;

import com.opencop.core.api.iam.ports.presenter.Presenter;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;

public class LayerMemberPresenter implements Presenter<LayerMember, LayerMember> {

  @Override
  public LayerMember present(LayerMember businessModel) {
    return businessModel;
  }
}
