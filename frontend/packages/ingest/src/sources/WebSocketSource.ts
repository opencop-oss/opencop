// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { OCMPClient } from "@opencop/websocket";
import { Source } from "./Source";
import Logger from "js-logger";
import { ElementMessage, SocketMessage } from "@opencop/proto";
import { getProcessor } from "../init-processor.util";
import type { Element } from "@opencop/elements";

const logger = Logger.get("ws_source");

export const destinationPrefix = "/topic/queue/event/";
export const messages = "/user/queue/messages";

export interface SyncMessage {
    source: string;
    state: "STARTING" | "FINISHED";
}

export function decodeMultiPayload(bytes: Uint8Array): Uint8Array[] {
    return SocketMessage.MultiPayload.decode(bytes).payload;
}

export function decodeElements(bytes: Uint8Array): Element[] | undefined {
    const processor = getProcessor();
    const elements: Element[] = [];

    for (const b of decodeMultiPayload(bytes)) {
        const elementMessage = ElementMessage.decode(b);
        const element: Element | undefined = processor.decode(elementMessage);
        if (element) {
            logger.debug("Decoded element: ", element);
            elements.push(element);
        }
    }

    if (elements.length > 0) {
        return elements;
    }
}

/** Websocket {@link Source source} using {@link OCMPClient}. */
export class WebSocketSource extends Source {
    private readonly client: OCMPClient;
    private readonly destination: string;
    private generation: number = 0;

    private sync!: (bytes: Uint8Array) => void;

    constructor(id: string, client: OCMPClient, destination: string) {
        super(id);
        this.client = client;
        this.destination = destination;
    }

    protected onEnable(): void {
        this.sync = this.getSync(++this.generation);
        this.client.subscribe(messages, this.sync);
        this.client.subscribe(this.destination, this.live);
    }

    protected onDisable(): void {
        this.client.unsubscribe(messages, this.sync);
        this.client.unsubscribe(this.destination, this.live);
    }

    private live = (bytes: Uint8Array) => {
        const elements = decodeElements(bytes);
        if (elements) this.handler?.(elements);
    };

    /** Ensure stale FINISHED messages are ignored. */
    private getSync = (generation: number) => (bytes: Uint8Array) => {
        try {
            // Decode the message as JSON
            const text = new TextDecoder().decode(bytes);
            const message: SyncMessage = JSON.parse(text);

            // Only handle FINISHED state messages
            if (
                message.state === "FINISHED" &&
                message.source === this.id &&
                generation === this.generation
            ) {
                this.setState("enabled");
            }
        } catch (error) {
            // Silently ignore malformed messages
            logger.warn("Failed to parse source sync state message:", error);
            this.setState("error");
        }
    };
}
