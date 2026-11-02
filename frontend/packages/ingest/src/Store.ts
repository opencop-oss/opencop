// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

export type ObjectRef = {
    type: string;
    key: string;
};

export type StoreEvent = "add" | "update" | "remove";
export interface Store<T = unknown> {
    readonly type: string;
    get(key: string): T | undefined;
    getAll(): Iterable<T>;
    ref(object: T): ObjectRef;

    on(event: StoreEvent, listener: (object: T[]) => void): void;
    off(event: StoreEvent, listener: (object: T[]) => void): void;
}
