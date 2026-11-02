// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import Vec from "./Vec";

export class VecI32 extends Vec<Int32Array> {
    constructor();
    constructor(capacity?: number);
    constructor(array: Int32Array);
    constructor(arg?: number | Int32Array) {
        super(Int32Array, arg);
    }
}
