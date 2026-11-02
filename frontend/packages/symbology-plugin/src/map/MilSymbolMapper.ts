// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { SymbolElement } from "@opencop/elements";
import type { ElementMapper, MapperSelector, MapperState } from "@opencop/contributions";
import { icon, label, type Color, type Primitive } from "@opencop/scene";
import * as turf from "@turf/turf";
import { Renderer } from "@opencop/symbology";

export type MilSymbolSource = {
    id?: string;
    url: string;
    width: number;
    height: number;
    anchorX?: number;
    anchorY?: number;
};

export type MilSymbolState = number;

export class MilSymbolSelector implements MapperSelector<MilSymbolState> {
    select(state: MapperState): number {
        return state.icons.size;
    }
}

export class MilSymbolMapper implements ElementMapper<SymbolElement, MilSymbolState> {
    public readonly type = "symbol" as const;
    public readonly selector = new MilSymbolSelector();

    map(object: SymbolElement, size: MilSymbolState): Primitive[] {
        const code = object.getCode() ?? "";
        const source: MilSymbolSource = Renderer.render({ sidc: code }).source;
        const { width, height } = source;
        const position = object.geometry.getPosition() as [number, number];
        const name = object.getDisplayName();

        const primitives: Primitive[] = [
            icon({
                id: "symbol",
                geometry: {
                    positions: [...position, 0],
                },
                style: {
                    id: source.id ?? source.url,
                    source: source.url,
                    width,
                    height,
                    size,
                    billboard: true,
                },
            }),
        ];
        if (name)
            primitives.push(
                label({
                    id: "label",
                    geometry: {
                        positions: [...position, 0],
                    },
                    style: {
                        text: name,
                        offset: [0, size * 0.75],
                    },
                }),
            );

        // Course arrow orbits the symbol, pointing along the course.
        const course = object.course;
        if (course !== undefined && course !== null) {
            const theta = turf.degreesToRadians(90 - course);
            const orbit = size * 1.25;
            primitives.push(
                icon({
                    id: "course",
                    geometry: {
                        positions: [...position, 0],
                    },
                    style: {
                        id: "course-arrow",
                        source: COURSE_ARROW_SOURCE,
                        width: 32,
                        height: 32,

                        size: size * 0.75,
                        angle: -course,
                        offset: [orbit * Math.cos(theta), -orbit * Math.sin(theta)],
                        billboard: false,

                        mask: true,
                        color: COURSE_ARROW_COLOR,
                    },
                }),
            );
        }

        return primitives;
    }
}

/** `MdArrowDropUp`, masked and tinted like `IconElementLayer`'s course arrow. */
const COURSE_ARROW_SOURCE = `data:image/svg+xml;utf8,${encodeURIComponent(
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="32" height="32">` +
        `<path d="M7 14l5-5 5 5z" fill="white"/></svg>`,
)}`;
const COURSE_ARROW_COLOR: Color = [200, 200, 200, 255];
