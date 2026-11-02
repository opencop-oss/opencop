// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ObjectRef } from "@opencop/ingest";

export type PrimitiveRef = {
    object: ObjectRef;
    nanoid: string;
};

export interface RefCodec {
    encode(ref: PrimitiveRef): string;
    decode(id: string): PrimitiveRef | undefined;
}

export const codec: RefCodec = {
    encode(ref: PrimitiveRef): string {
        return (
            encodeURIComponent(ref.object.type) +
            ":" +
            ref.object.key +
            ":" +
            encodeURIComponent(ref.nanoid)
        );
    },
    decode(id: string): PrimitiveRef | undefined {
        // Keys may contain ":"; encoded types and nanoids never do.
        const first = id.indexOf(":");
        const last = id.lastIndexOf(":");
        if (first < 0 || first === last) return undefined;
        try {
            return {
                object: {
                    type: decodeURIComponent(id.slice(0, first)),
                    key: id.slice(first + 1, last),
                },
                nanoid: decodeURIComponent(id.slice(last + 1)),
            };
        } catch {
            return undefined;
        }
    },
};
