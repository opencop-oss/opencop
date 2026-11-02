// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import Vec from "./Vec";

export class VecF32 extends Vec<Float32Array> {
    constructor();
    constructor(capacity?: number);
    constructor(array: Float32Array);
    constructor(arg?: number | Float32Array) {
        super(Float32Array, arg);
    }
}
