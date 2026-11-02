// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

// The style-input contract (StyleSpec + input specs) lives in `@opencop/tools` —
// a subject's formatting-bar inputs are part of what it contributes. Re-exported
// here beside the narrowed spec types the toolbar's inputs render.
export type {
    StyleSpec,
    InputSpec,
    SliderSpec,
    SwatchSpec,
    NumberSpec,
    SelectSpec,
    WeightSpec,
    PatternSpec,
} from "@opencop/tools";
import type {
    StyleSpec,
    SwatchSpec,
    SliderSpec,
    NumberSpec,
    SelectSpec,
    WeightSpec,
    PatternSpec,
} from "@opencop/tools";

export interface SwatchStyleSpec extends StyleSpec {
    input: SwatchSpec;
}

export interface SliderStyleSpec extends StyleSpec {
    input: SliderSpec;
}

export interface NumberStyleSpec extends StyleSpec {
    input: NumberSpec;
}

export interface SelectStyleSpec extends StyleSpec {
    input: SelectSpec;
}

export interface WeightStyleSpec extends StyleSpec {
    input: WeightSpec;
}

export interface PatternStyleSpec extends StyleSpec {
    input: PatternSpec;
}
