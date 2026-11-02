// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useEffect, useReducer, useRef } from "react";

function str(replacement: string, num: number): string {
    let str = "";
    for (let i = 0; i < num; i++) {
        str += replacement;
    }

    return str;
}

/** TODO: Dead code — no consumers in the repo; delete or justify in MR 1.3 (package hygiene). */
export function useOvertype(value: string, onChange: (v: string) => void, replacement?: string) {
    const [, forceUpdate] = useReducer((x) => x + 1, 0);
    const ref = useRef<HTMLInputElement>(null);
    const caretRef = useRef({
        position: 0,
        pendingOffset: 0,
    });

    if (replacement && replacement.length !== 1)
        throw new Error("[useOvertype]: REPLACEMENT MUST BE A SINGLE CHAR.");

    useEffect(() => {
        const { current: el } = ref;
        const { current: caret } = caretRef;
        if (el && caret.pendingOffset !== 0) {
            el.setSelectionRange(
                caret.position + caret.pendingOffset,
                caret.position + caret.pendingOffset,
            );
            caret.pendingOffset = 0;
        }
    });

    const onBeforeInput = (e: React.InputEvent<HTMLInputElement>) => {
        const ev = e.nativeEvent as InputEvent;
        let { data } = ev;
        const { current: caret } = caretRef;
        if (!data) return;

        const el = ref.current!;
        const start = el.selectionStart!;
        const end = el.selectionEnd!;
        const diff = end - start;

        caret.position = start;

        if (!diff) {
            if (start + data.length > value.length) {
                const maxLen = value.length - start;
                data = data.slice(0, maxLen);
            }
        } else {
            data = data.slice(0, diff);
        }

        e.preventDefault();

        caret.pendingOffset += data.length;
        const v = value.slice(0, start) + data + value.slice(start + data.length);

        forceUpdate();
        onChange(v);
    };

    const onKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
        if (e.key === "Backspace" || e.key === "Delete") {
            const { current: caret } = caretRef;
            const el = ref.current!;
            const start = el.selectionStart!;
            const end = el.selectionEnd!;
            const diff = end - start;
            const pos = Math.max(end - 1, 0);

            if (replacement && start > 0) {
                const v =
                    value.slice(0, !diff ? start - 1 : start) +
                    str(replacement, !diff ? 1 : diff) +
                    value.slice(!diff ? start : end);
                onChange(v);
            }

            caret.position = pos;
            forceUpdate();
            e.preventDefault();
        }
    };

    return {
        ref,
        handlers: {
            onKeyDown,
            onBeforeInput,
        },
    };
}
