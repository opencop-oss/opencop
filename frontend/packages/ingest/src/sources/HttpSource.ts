// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Source } from "./Source";

/**
 * Http {@link Source}, possibly polling.
 *
 * @internal Unimplemented, here to convey extensibility.
 */
export class _HttpSource extends Source {
    protected onEnable(): void {
        throw new Error("Method not implemented.");
    }
    protected onDisable(): void {
        throw new Error("Method not implemented.");
    }
}
