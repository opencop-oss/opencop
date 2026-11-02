// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.ports.presenter;

import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import java.util.List;

public interface ConnectionManagerPresenter<O> {

  O present(ConnectionSpec value);

  List<O> present(List<O> value);
}
