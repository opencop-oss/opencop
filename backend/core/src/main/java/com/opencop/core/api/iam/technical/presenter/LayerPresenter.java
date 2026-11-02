// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.technical.presenter;

import com.opencop.core.api.iam.ports.presenter.Presenter;
import com.opencop.core.sdk.business.model.iam.access.Layer;

public class LayerPresenter implements Presenter<Layer, Layer> {
  @Override
  public Layer present(Layer businessModel) {
    return businessModel;
  }
}
