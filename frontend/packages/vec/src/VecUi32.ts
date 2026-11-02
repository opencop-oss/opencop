// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import Vec from "./Vec";

export class VecUi32 extends Vec<Uint32Array> {
    constructor();
    constructor(capacity?: number);
    constructor(array: Uint32Array);
    constructor(arg?: number | Uint32Array) {
        super(Uint32Array, arg);
    }
}
