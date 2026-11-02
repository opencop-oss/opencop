// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { WebsocketBuilder, type Websocket } from "websocket-ts";
import type { Backoff, WebsocketTransport, WebsocketTransportBuilder } from "./WebsocketTransport";

export class WebsocketTsTransport implements WebsocketTransport {
    private readonly ws: Websocket;

    constructor(builder: WebsocketTsTransportBuilder) {
        this.ws = builder.builder.build();
    }

    isOpen(): boolean {
        return this.ws.underlyingWebsocket.readyState === WebSocket.OPEN;
    }

    isClosed(): boolean {
        return this.ws.underlyingWebsocket.readyState === WebSocket.CLOSED;
    }

    isConnecting(): boolean {
        return this.ws.underlyingWebsocket.readyState === WebSocket.CONNECTING;
    }

    // websocket-ts drops data sent while disconnected because no buffer is configured; the
    // interface forbids one, so this is the required behaviour rather than an accident.
    send(data: string | ArrayBufferLike | Blob | ArrayBufferView): void {
        this.ws.send(data);
    }

    close(): void {
        this.ws.close();
    }
}

export class WebsocketTsTransportBuilder implements WebsocketTransportBuilder {
    public builder: WebsocketBuilder;

    // No buffer: the transport contract forbids queueing.
    constructor(url: string) {
        this.builder = new WebsocketBuilder(url);
    }

    withBackoff(backoff?: Backoff): this {
        this.builder = this.builder.withBackoff(backoff);
        return this;
    }

    onOpen(listener: () => void): this {
        this.builder = this.builder.onOpen(listener);
        return this;
    }

    onClose(listener: (event: CloseEvent) => void): this {
        this.builder = this.builder.onClose((_, event) => listener(event));
        return this;
    }

    onError(listener: (event: Event) => void): this {
        this.builder = this.builder.onError((_, ev) => listener(ev));
        return this;
    }

    onRetry(listener: () => void): this {
        this.builder = this.builder.onRetry(listener);
        return this;
    }

    onReconnect(listener: () => void): this {
        this.builder = this.builder.onReconnect(listener);
        return this;
    }

    onMessage(listener: (ev: MessageEvent) => void): this {
        this.builder = this.builder.onMessage((_, ev) => listener(ev));
        return this;
    }

    build(): WebsocketTransport {
        return new WebsocketTsTransport(this);
    }
}
