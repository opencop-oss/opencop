// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Style } from "./Style";

export class BaseStyle extends Style {
    /** Inverse of {@link toProperties}. Reconstructs a {@link BaseStyle} from a serialized property bag. */
    static fromProperties(props: Record<string, unknown> = {}): BaseStyle {
        void props;
        return new BaseStyle();
    }

    clone(): Style {
        return new BaseStyle();
    }
    toProperties(): Record<string, unknown> {
        return {};
    }
}
