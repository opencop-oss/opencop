// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Polyline } from "../geometry/Polyline";
import type { TypeDef, TypeDefProps } from "./TypeDef";
import { Element } from "../Element";
import { BaseStyle } from "../style/BaseStyle";

export interface GraphicProps extends TypeDefProps {
    sidc: string;
    controls: number[];
    modifiers?: Record<string, string>;
}

export type GraphicElement = Element<Polyline, BaseStyle>;

export const Graphic = {
    type: "graphic",

    is(element: Element): element is Element<Polyline> & {
        getCode(): string;
    } {
        return element.type === this.type && !!element.getCode();
    },

    create({ sidc, controls, style, modifiers, meta }: GraphicProps): Element<Polyline> {
        return new Element({
            ...meta,
            modifiers,
            type: this.type,
            code: sidc,
            geometry: new Polyline(controls, 2),
            style: style ?? new BaseStyle(),
        });
    },
} as const satisfies TypeDef<Polyline>;
