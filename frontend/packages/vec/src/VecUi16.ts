// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import Vec from "./Vec";

export class VecUi16 extends Vec<Uint16Array> {
    constructor();
    constructor(capacity?: number);
    constructor(array: Uint16Array);
    constructor(arg?: number | Uint16Array) {
        super(Uint16Array, arg);
    }
}
