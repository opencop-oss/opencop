// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

type Key = string | number | symbol;

/**
 * A bijective record, meaning it preserves the following properties:
 * - BijectiveRecord.get(a) = BijectiveRecord.get(b)` if and only if `a = b`.
 * - For each `value` in `BijectiveRecord`, there exists a `key` such that `BijectiveRecord.get(key) = value`.
 *
 * @template I The type of the key.
 * @template O The type of the value.
 */
export class BijectiveRecord<I extends Key, O extends Key> {
    // We define the 'forward' function as f(x) = forward[x].
    // We construct f^-1 as f^-1(y) = reverse[y].
    protected forward: Partial<Record<I, O>>;
    protected reverse: Partial<Record<O, I>>;

    constructor() {
        this.forward = {};
        this.reverse = {};
    }

    /**
     * Adds the key to the domain, value to the co-domain, and sets f(key) = value.
     *
     * If either key or value already exists in their respective domains, their mappings are overwritten.
     * @param key
     * @param value
     */
    set(key: I, value: O): void {
        /** Maintain injectivity.
         * @example
         * set('a', 1);
         * set('b', 1);
         *
         * forward['a'] needs to be removed from domain as f(a) = f(b) iff a = b to be injective.
         */
        if (this.reverse[value] !== undefined) delete this.forward[this.reverse[value]];
        /** Maintain surjectivity.
         * @example
         * set('a', 1);
         * set('a', 2);
         *
         * reverse[1] needs to be removed from co-domain as forward['a'] no longer maps to it.
         */
        if (this.forward[key] !== undefined) delete this.reverse[this.forward[key]];

        this.forward[key] = value;
        this.reverse[value] = key;
    }

    delete(key: I): void;
    delete(value: O): void;
    delete(keyOrValue: I | O): void {
        if (keyOrValue in this.forward) {
            delete this.reverse[this.forward[keyOrValue as I] as O];
            delete this.forward[keyOrValue as I];
        } else if (keyOrValue in this.reverse) {
            delete this.forward[this.reverse[keyOrValue as O] as I];
            delete this.reverse[keyOrValue as O];
        }
    }

    swap(key0: I, key1: I): void;
    swap(value0: O, value1: O): void;
    swap<K extends I | O>(keyOrValue0: K, keyOrValue1: K): void {
        if (keyOrValue0 in this.forward && keyOrValue1 in this.forward) {
            const k0: I = keyOrValue0 as I;
            const k1: I = keyOrValue1 as I;
            const temp: O = this.forward[k0] as O;
            this.forward[k0] = this.forward[k1];
            this.reverse[this.forward[k0] as O] = k0;

            this.forward[k1] = temp;
            this.reverse[temp] = k1;
        } else if (keyOrValue0 in this.reverse && keyOrValue1 in this.reverse) {
            const v0: O = keyOrValue0 as O;
            const v1: O = keyOrValue1 as O;
            const temp: I = this.reverse[v0] as I;

            this.reverse[v0] = this.reverse[v1];
            this.forward[this.reverse[v0] as I] = v0;

            this.reverse[v1] = temp;
            this.forward[temp] = v1;
        }
    }

    // The only case where this overload approach might not seem ideal is with a reflexive map of f(x) = x, though in that case
    // it suffices to return either the key or the value as they are equal.
    get(key: I): O | undefined;
    get(value: O): I | undefined;
    get(keyOrValue: I | O): I | O | undefined {
        if (keyOrValue in this.forward) {
            return this.forward[keyOrValue as I];
        } else if (keyOrValue in this.reverse) {
            return this.reverse[keyOrValue as O];
        }
    }
}
