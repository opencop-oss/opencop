// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { MobacError } from "./mobac-error";
import Logger from "js-logger";

const logger = Logger.get("imagery");

type CustomMapSourceSpec = {
    name: string;
    url: string;

    tileType: "png" | "jpg";
    tileUpdate: "None";

    minZoom: number;
    maxZoom: number;

    backgroundColor?: string;
    ignoreErrors?: boolean;
};

export class CustomMapSource {
    private readonly name: string;
    private readonly url: string;

    private readonly tileType: "png" | "jpg";
    private readonly tileUpdate: "None";

    private readonly minZoom: number;
    private readonly maxZoom: number;

    private readonly backgroundColor?: string;
    private readonly ignoreErrors?: boolean;

    private constructor(spec: CustomMapSourceSpec) {
        this.name = spec.name;
        this.url = spec.url;
        this.tileType = spec.tileType;
        this.tileUpdate = spec.tileUpdate;
        this.minZoom = spec.minZoom;
        this.maxZoom = spec.maxZoom;
        this.backgroundColor = spec.backgroundColor;
        this.ignoreErrors = spec.ignoreErrors;
    }

    static fromXML<T extends string>(xml: T): CustomMapSource | undefined {
        try {
            const doc: Document = new DOMParser().parseFromString(xml, "application/xml");

            const custom: Element | null = doc.querySelector("customMapSource");
            if (!custom) throw new MobacError("Missing <customMapSource> element");

            const record: CustomMapSourceSpec = {
                name: "",
                url: "",
                tileType: "png",
                tileUpdate: "None",
                minZoom: 0,
                maxZoom: 0,
            };

            ["name" as const, "url" as const].forEach((tag) => {
                record[tag] = this.validate(custom, tag).textContent!;
            });

            // replace all {$x}, {$y}, {$z} w/ {x}, {y}, {z}
            record.url = record.url.replace(/\{\$(x|y|z)\}/g, (_, p1) => `{${p1}}`);
            // replace {$q} with {quadkey}
            record.url = record.url.replace(/\{\$q\}/g, "{quadkey}");

            ["minZoom" as const, "maxZoom" as const].forEach((tag) => {
                const element: Element = this.validate(custom, tag);
                record[tag] = Number(element.textContent!);
            });

            let content: string = this.validate(custom, "tileType").textContent!;

            if (content !== "png" && content !== "jpg")
                throw new MobacError("<tileType> element is not 'png' or 'jpg'");
            record["tileType"] = content as "png" | "jpg";

            content = this.validate(custom, "tileUpdate").textContent!;
            if (content !== "None") throw new MobacError("<tileUpdate> element is not 'None'");

            return new CustomMapSource(record);
        } catch (error) {
            if (import.meta.env.DEV) logger.error("Error parsing MOBAC XML: " + error);
        }

        return undefined;
    }

    private static validate(parent: Element, tag: string): Element {
        const element: Element | null = parent.querySelector(tag);
        if (!element) throw new MobacError(`Missing <${tag}> element`);
        if (!element.textContent) throw new MobacError(`<${tag}> element has no innerText`);

        return element;
    }

    getName(): string {
        return this.name;
    }

    getUrl(): string {
        return this.url;
    }

    getTileType(): string {
        return this.tileType;
    }

    getTileUpdate(): string {
        return this.tileUpdate;
    }

    getMinZoom(): number {
        return this.minZoom;
    }

    getMaxZoom(): number {
        return this.maxZoom;
    }

    getBackgroundColor(): string | undefined {
        return this.backgroundColor;
    }

    getIgnoreErrors(): boolean | undefined {
        return this.ignoreErrors;
    }
}
