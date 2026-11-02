// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import * as $protobuf from "protobufjs";
import Long = require("long");

/**
 * Properties of a SocketMessage.
 * @deprecated Use SocketMessage.$Properties instead.
 */
export interface ISocketMessage extends SocketMessage.$Properties {
}

/** Represents a SocketMessage. */
export class SocketMessage {

    /**
     * Constructs a new SocketMessage.
     * @param [properties] Properties to set
     */
    constructor(properties?: SocketMessage.$Properties);

    /** Unknown fields preserved while decoding when enabled */
    $unknowns?: Uint8Array[];

    /** SocketMessage type. */
    type: SocketMessage.Type;

    /** SocketMessage destination. */
    destination: string;

    /** SocketMessage headers. */
    headers: { [k: string]: string };

    /** SocketMessage payload. */
    payload: Uint8Array;

    /** SocketMessage compressed. */
    compressed: boolean;

    /**
     * Creates a new SocketMessage instance using the specified properties.
     * @param [properties] Properties to set
     * @returns SocketMessage instance
     */
    static create(properties: SocketMessage.$Shape): SocketMessage & SocketMessage.$Shape;
    static create(properties?: SocketMessage.$Properties): SocketMessage;

    /**
     * Encodes the specified SocketMessage message. Does not implicitly {@link SocketMessage.verify|verify} messages.
     * @param message SocketMessage message or plain object to encode
     * @param [writer] Writer to encode to
     * @returns Writer
     */
    static encode(message: SocketMessage.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

    /**
     * Encodes the specified SocketMessage message, length delimited. Does not implicitly {@link SocketMessage.verify|verify} messages.
     * @param message SocketMessage message or plain object to encode
     * @param [writer] Writer to encode to
     * @returns Writer
     */
    static encodeDelimited(message: SocketMessage.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

    /**
     * Decodes a SocketMessage message from the specified reader or buffer.
     * @param reader Reader or buffer to decode from
     * @param [length] Message length if known beforehand
     * @returns {SocketMessage & SocketMessage.$Shape} SocketMessage
     * @throws {Error} If the payload is not a reader or valid buffer
     * @throws {$protobuf.util.ProtocolError} If required fields are missing
     */
    static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): SocketMessage & SocketMessage.$Shape;

    /**
     * Decodes a SocketMessage message from the specified reader or buffer, length delimited.
     * @param reader Reader or buffer to decode from
     * @returns {SocketMessage & SocketMessage.$Shape} SocketMessage
     * @throws {Error} If the payload is not a reader or valid buffer
     * @throws {$protobuf.util.ProtocolError} If required fields are missing
     */
    static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): SocketMessage & SocketMessage.$Shape;

    /**
     * Verifies a SocketMessage message.
     * @param message Plain object to verify
     * @returns `null` if valid, otherwise the reason why it is not
     */
    static verify(message: { [k: string]: any }): (string|null);

    /**
     * Creates a SocketMessage message from a plain object. Also converts values to their respective internal types.
     * @param object Plain object
     * @returns SocketMessage
     */
    static fromObject(object: { [k: string]: any }): SocketMessage;

    /**
     * Creates a plain object from a SocketMessage message. Also converts values to other types if specified.
     * @param message SocketMessage
     * @param [options] Conversion options
     * @returns Plain object
     */
    static toObject(message: SocketMessage, options?: $protobuf.IConversionOptions): { [k: string]: any };

    /**
     * Converts this SocketMessage to JSON.
     * @returns JSON object
     */
    toJSON(): { [k: string]: any };

    /**
     * Gets the type url for SocketMessage
     * @param [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
     * @returns The type url
     */
    static getTypeUrl(prefix?: string): string;
}

export namespace SocketMessage {

    /** Properties of a SocketMessage. */
    interface $Properties {

        /** SocketMessage type */
        type?: (SocketMessage.Type|null);

        /** SocketMessage destination */
        destination?: (string|null);

        /** SocketMessage headers */
        headers?: ({ [k: string]: string }|null);

        /** SocketMessage payload */
        payload?: (Uint8Array|null);

        /** SocketMessage compressed */
        compressed?: (boolean|null);

        /** Unknown fields preserved while decoding when enabled */
        $unknowns?: Uint8Array[];
    }

    /** Shape of a SocketMessage. */
    type $Shape = SocketMessage.$Properties;

    /** Type enum. */
    enum Type {

        /** UNKNOWN value */
        UNKNOWN = 0,

        /** CONNECT value */
        CONNECT = 1,

        /** SUBSCRIBE value */
        SUBSCRIBE = 2,

        /** SEND value */
        SEND = 3,

        /** UNSUBSCRIBE value */
        UNSUBSCRIBE = 4,

        /** HEARTBEAT value */
        HEARTBEAT = 5,

        /** ERROR value */
        ERROR = 6
    }

    /**
     * Properties of a MultiPayload.
     * @deprecated Use SocketMessage.MultiPayload.$Properties instead.
     */
    interface IMultiPayload extends SocketMessage.MultiPayload.$Properties {
    }

    /** Represents a MultiPayload. */
    class MultiPayload {

        /**
         * Constructs a new MultiPayload.
         * @param [properties] Properties to set
         */
        constructor(properties?: SocketMessage.MultiPayload.$Properties);

        /** Unknown fields preserved while decoding when enabled */
        $unknowns?: Uint8Array[];

        /** MultiPayload payload. */
        payload: Uint8Array[];

        /**
         * Creates a new MultiPayload instance using the specified properties.
         * @param [properties] Properties to set
         * @returns MultiPayload instance
         */
        static create(properties: SocketMessage.MultiPayload.$Shape): SocketMessage.MultiPayload & SocketMessage.MultiPayload.$Shape;
        static create(properties?: SocketMessage.MultiPayload.$Properties): SocketMessage.MultiPayload;

        /**
         * Encodes the specified MultiPayload message. Does not implicitly {@link SocketMessage.MultiPayload.verify|verify} messages.
         * @param message MultiPayload message or plain object to encode
         * @param [writer] Writer to encode to
         * @returns Writer
         */
        static encode(message: SocketMessage.MultiPayload.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

        /**
         * Encodes the specified MultiPayload message, length delimited. Does not implicitly {@link SocketMessage.MultiPayload.verify|verify} messages.
         * @param message MultiPayload message or plain object to encode
         * @param [writer] Writer to encode to
         * @returns Writer
         */
        static encodeDelimited(message: SocketMessage.MultiPayload.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

        /**
         * Decodes a MultiPayload message from the specified reader or buffer.
         * @param reader Reader or buffer to decode from
         * @param [length] Message length if known beforehand
         * @returns {SocketMessage.MultiPayload & SocketMessage.MultiPayload.$Shape} MultiPayload
         * @throws {Error} If the payload is not a reader or valid buffer
         * @throws {$protobuf.util.ProtocolError} If required fields are missing
         */
        static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): SocketMessage.MultiPayload & SocketMessage.MultiPayload.$Shape;

        /**
         * Decodes a MultiPayload message from the specified reader or buffer, length delimited.
         * @param reader Reader or buffer to decode from
         * @returns {SocketMessage.MultiPayload & SocketMessage.MultiPayload.$Shape} MultiPayload
         * @throws {Error} If the payload is not a reader or valid buffer
         * @throws {$protobuf.util.ProtocolError} If required fields are missing
         */
        static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): SocketMessage.MultiPayload & SocketMessage.MultiPayload.$Shape;

        /**
         * Verifies a MultiPayload message.
         * @param message Plain object to verify
         * @returns `null` if valid, otherwise the reason why it is not
         */
        static verify(message: { [k: string]: any }): (string|null);

        /**
         * Creates a MultiPayload message from a plain object. Also converts values to their respective internal types.
         * @param object Plain object
         * @returns MultiPayload
         */
        static fromObject(object: { [k: string]: any }): SocketMessage.MultiPayload;

        /**
         * Creates a plain object from a MultiPayload message. Also converts values to other types if specified.
         * @param message MultiPayload
         * @param [options] Conversion options
         * @returns Plain object
         */
        static toObject(message: SocketMessage.MultiPayload, options?: $protobuf.IConversionOptions): { [k: string]: any };

        /**
         * Converts this MultiPayload to JSON.
         * @returns JSON object
         */
        toJSON(): { [k: string]: any };

        /**
         * Gets the type url for MultiPayload
         * @param [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
         * @returns The type url
         */
        static getTypeUrl(prefix?: string): string;
    }

    namespace MultiPayload {

        /** Properties of a MultiPayload. */
        interface $Properties {

            /** MultiPayload payload */
            payload?: (Uint8Array[]|null);

            /** Unknown fields preserved while decoding when enabled */
            $unknowns?: Uint8Array[];
        }

        /** Shape of a MultiPayload. */
        type $Shape = SocketMessage.MultiPayload.$Properties;
    }
}
