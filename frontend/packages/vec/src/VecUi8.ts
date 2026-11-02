// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import Vec from "./Vec";

export class VecUi8 extends Vec<Uint8Array> {
    constructor();
    constructor(capacity?: number);
    constructor(array: Uint8Array);
    constructor(arg?: number | Uint8Array) {
        super(Uint8Array, arg);
    }
}
