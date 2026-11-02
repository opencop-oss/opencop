// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

// Never import opencc-js statically: the Simplified-to-Traditional dictionary is ~1 MB and would
// land in the main bundle.

const HAS_HAN = /\p{Script=Han}/u;

type Converter = (text: string) => string;

let traditionalConverter: Promise<Converter> | undefined;

function getTraditionalConverter(): Promise<Converter> {
    traditionalConverter ??= Promise.all([
        import("opencc-js/core"),
        import("opencc-js/preset/cn2t"),
    ])
        .then(([core, preset]) => core.ConverterFactory(preset.from.cn.concat(preset.to.tw)))
        // Drop a failed chunk load so a flaky fetch doesn't break display for the whole session.
        .catch((err: unknown) => {
            traditionalConverter = undefined;
            throw err;
        });
    return traditionalConverter;
}

/** Converts display text only. Coordinates and ids never pass through here. */
export async function toTraditionalDisplay(text: string): Promise<string> {
    if (!HAS_HAN.test(text)) return text;
    return (await getTraditionalConverter())(text);
}
