// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.ports.presenter;

public interface Presenter<O, I> {

  O present(I businessModel);
}
