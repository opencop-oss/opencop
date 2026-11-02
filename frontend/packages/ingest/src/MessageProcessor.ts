// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Element } from "@opencop/elements";
import type { ElementMessage } from "@opencop/proto";
import Logger from "js-logger";
import type { ElementMapper } from "./mappers/ElementMapper";
import { buildMeta, type DecodeOptions } from "./mappers/mappers.utils";

const logger = Logger.get("ws");

/**
 * Maps `ElementMessages` to `Elements` and vice versa.
 */
export class MessageProcessor {
    // TODO: Simplify Element class to avoid custom mappers.
    private readonly mappers: Record<string, ElementMapper> = {};

    /**
     * Register a mapper to the processor.
     *
     * @param type The uint32 code of the element in a `ElementMessage`.
     * @param element The string type of the `Element`.
     * @param mapper The mapper.
     * @returns `this`
     */
    register(element: string, mapper: ElementMapper): this {
        logger.debug("Registered mapper for", element);
        this.mappers[element] = mapper;
        return this;
    }

    /**
     * Decode an `ElementMessage`.
     *
     * Extracts identity metadata once via {@link buildMeta} and passes it
     * to the registered mapper. Options flow through meta (e.g. historical
     * mode sets `elementClass: "historical"` so the Element constructor
     * picks the id-based key strategy).
     *
     * @param message The `ElementMessage`.
     * @param options Optional decode-time overrides (e.g. elementClass).
     * @returns The decoded `Element` or `undefined` if no mapper was found.
     */
    decode(message: ElementMessage, options?: DecodeOptions): Element | undefined {
        const mapper: ElementMapper | undefined = this.mappers[message.type];

        if (mapper) {
            return mapper.fromMessage(message, buildMeta(message, options));
        }

        logger.warn("No mapper registered for element type:", message.type);
    }
}
