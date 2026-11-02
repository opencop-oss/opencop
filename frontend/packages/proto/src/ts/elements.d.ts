// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import * as $protobuf from "protobufjs";
import Long = require("long");

/**
 * Properties of a Value.
 * @deprecated Use Value.$Properties instead.
 */
export interface IValue extends Value.$Properties {
}

/** Represents a Value. */
export class Value {

    /**
     * Constructs a new Value.
     * @param [properties] Properties to set
     */
    constructor(properties?: Value.$Properties);

    /** Unknown fields preserved while decoding when enabled */
    $unknowns?: Uint8Array[];

    /** Value int_32. */
    int_32?: (number|null);

    /** Value int_32Arr. */
    int_32Arr?: (RepeatedInt32.$Properties|null);

    /** Value int_64. */
    int_64?: (number|Long|null);

    /** Value int_64Arr. */
    int_64Arr?: (RepeatedInt64.$Properties|null);

    /** Value float. */
    float?: (number|null);

    /** Value floatArr. */
    floatArr?: (RepeatedFloat.$Properties|null);

    /** Value double. */
    double?: (number|null);

    /** Value doubleArr. */
    doubleArr?: (RepeatedDouble.$Properties|null);

    /** Value string. */
    string?: (string|null);

    /** Value bool. */
    bool?: (boolean|null);

    /** Value kind. */
    kind?: ("int_32"|"int_32Arr"|"int_64"|"int_64Arr"|"float"|"floatArr"|"double"|"doubleArr"|"string"|"bool");

    /**
     * Creates a new Value instance using the specified properties.
     * @param [properties] Properties to set
     * @returns Value instance
     */
    static create(properties: Value.$Shape): Value & Value.$Shape;
    static create(properties?: Value.$Properties): Value;

    /**
     * Encodes the specified Value message. Does not implicitly {@link Value.verify|verify} messages.
     * @param message Value message or plain object to encode
     * @param [writer] Writer to encode to
     * @returns Writer
     */
    static encode(message: Value.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

    /**
     * Encodes the specified Value message, length delimited. Does not implicitly {@link Value.verify|verify} messages.
     * @param message Value message or plain object to encode
     * @param [writer] Writer to encode to
     * @returns Writer
     */
    static encodeDelimited(message: Value.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

    /**
     * Decodes a Value message from the specified reader or buffer.
     * @param reader Reader or buffer to decode from
     * @param [length] Message length if known beforehand
     * @returns {Value & Value.$Shape} Value
     * @throws {Error} If the payload is not a reader or valid buffer
     * @throws {$protobuf.util.ProtocolError} If required fields are missing
     */
    static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): Value & Value.$Shape;

    /**
     * Decodes a Value message from the specified reader or buffer, length delimited.
     * @param reader Reader or buffer to decode from
     * @returns {Value & Value.$Shape} Value
     * @throws {Error} If the payload is not a reader or valid buffer
     * @throws {$protobuf.util.ProtocolError} If required fields are missing
     */
    static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): Value & Value.$Shape;

    /**
     * Verifies a Value message.
     * @param message Plain object to verify
     * @returns `null` if valid, otherwise the reason why it is not
     */
    static verify(message: { [k: string]: any }): (string|null);

    /**
     * Creates a Value message from a plain object. Also converts values to their respective internal types.
     * @param object Plain object
     * @returns Value
     */
    static fromObject(object: { [k: string]: any }): Value;

    /**
     * Creates a plain object from a Value message. Also converts values to other types if specified.
     * @param message Value
     * @param [options] Conversion options
     * @returns Plain object
     */
    static toObject(message: Value, options?: $protobuf.IConversionOptions): { [k: string]: any };

    /**
     * Converts this Value to JSON.
     * @returns JSON object
     */
    toJSON(): { [k: string]: any };

    /**
     * Gets the type url for Value
     * @param [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
     * @returns The type url
     */
    static getTypeUrl(prefix?: string): string;
}

export namespace Value {

    /** Properties of a Value. */
    interface $Properties {

        /** Value int_32 */
        int_32?: (number|null);

        /** Value int_32Arr */
        int_32Arr?: (RepeatedInt32.$Properties|null);

        /** Value int_64 */
        int_64?: (number|Long|null);

        /** Value int_64Arr */
        int_64Arr?: (RepeatedInt64.$Properties|null);

        /** Value float */
        float?: (number|null);

        /** Value floatArr */
        floatArr?: (RepeatedFloat.$Properties|null);

        /** Value double */
        double?: (number|null);

        /** Value doubleArr */
        doubleArr?: (RepeatedDouble.$Properties|null);

        /** Value string */
        string?: (string|null);

        /** Value bool */
        bool?: (boolean|null);

        /** Value kind */
        kind?: ("int_32"|"int_32Arr"|"int_64"|"int_64Arr"|"float"|"floatArr"|"double"|"doubleArr"|"string"|"bool");

        /** Unknown fields preserved while decoding when enabled */
        $unknowns?: Uint8Array[];
    }

    /** Narrowed shape of a Value. */
    type $Shape = {
      int_32?: number|null;
      int_32Arr?: RepeatedInt32.$Shape|null;
      int_64?: number|Long|null;
      int_64Arr?: RepeatedInt64.$Shape|null;
      float?: number|null;
      floatArr?: RepeatedFloat.$Shape|null;
      double?: number|null;
      doubleArr?: RepeatedDouble.$Shape|null;
      string?: string|null;
      bool?: boolean|null;
      $unknowns?: Uint8Array[];
    } & (
      ({ kind?: undefined; int_32?: null; int_32Arr?: null; int_64?: null; int_64Arr?: null; float?: null; floatArr?: null; double?: null; doubleArr?: null; string?: null; bool?: null }|{ kind?: "int_32"; int_32: number; int_32Arr?: null; int_64?: null; int_64Arr?: null; float?: null; floatArr?: null; double?: null; doubleArr?: null; string?: null; bool?: null }|{ kind?: "int_32Arr"; int_32?: null; int_32Arr: RepeatedInt32.$Shape; int_64?: null; int_64Arr?: null; float?: null; floatArr?: null; double?: null; doubleArr?: null; string?: null; bool?: null }|{ kind?: "int_64"; int_32?: null; int_32Arr?: null; int_64: number|Long; int_64Arr?: null; float?: null; floatArr?: null; double?: null; doubleArr?: null; string?: null; bool?: null }|{ kind?: "int_64Arr"; int_32?: null; int_32Arr?: null; int_64?: null; int_64Arr: RepeatedInt64.$Shape; float?: null; floatArr?: null; double?: null; doubleArr?: null; string?: null; bool?: null }|{ kind?: "float"; int_32?: null; int_32Arr?: null; int_64?: null; int_64Arr?: null; float: number; floatArr?: null; double?: null; doubleArr?: null; string?: null; bool?: null }|{ kind?: "floatArr"; int_32?: null; int_32Arr?: null; int_64?: null; int_64Arr?: null; float?: null; floatArr: RepeatedFloat.$Shape; double?: null; doubleArr?: null; string?: null; bool?: null }|{ kind?: "double"; int_32?: null; int_32Arr?: null; int_64?: null; int_64Arr?: null; float?: null; floatArr?: null; double: number; doubleArr?: null; string?: null; bool?: null }|{ kind?: "doubleArr"; int_32?: null; int_32Arr?: null; int_64?: null; int_64Arr?: null; float?: null; floatArr?: null; double?: null; doubleArr: RepeatedDouble.$Shape; string?: null; bool?: null }|{ kind?: "string"; int_32?: null; int_32Arr?: null; int_64?: null; int_64Arr?: null; float?: null; floatArr?: null; double?: null; doubleArr?: null; string: string; bool?: null }|{ kind?: "bool"; int_32?: null; int_32Arr?: null; int_64?: null; int_64Arr?: null; float?: null; floatArr?: null; double?: null; doubleArr?: null; string?: null; bool: boolean })
    );
}

/**
 * Properties of a RepeatedInt32.
 * @deprecated Use RepeatedInt32.$Properties instead.
 */
export interface IRepeatedInt32 extends RepeatedInt32.$Properties {
}

/** Represents a RepeatedInt32. */
export class RepeatedInt32 {

    /**
     * Constructs a new RepeatedInt32.
     * @param [properties] Properties to set
     */
    constructor(properties?: RepeatedInt32.$Properties);

    /** Unknown fields preserved while decoding when enabled */
    $unknowns?: Uint8Array[];

    /** RepeatedInt32 numbers. */
    numbers: number[];

    /**
     * Creates a new RepeatedInt32 instance using the specified properties.
     * @param [properties] Properties to set
     * @returns RepeatedInt32 instance
     */
    static create(properties: RepeatedInt32.$Shape): RepeatedInt32 & RepeatedInt32.$Shape;
    static create(properties?: RepeatedInt32.$Properties): RepeatedInt32;

    /**
     * Encodes the specified RepeatedInt32 message. Does not implicitly {@link RepeatedInt32.verify|verify} messages.
     * @param message RepeatedInt32 message or plain object to encode
     * @param [writer] Writer to encode to
     * @returns Writer
     */
    static encode(message: RepeatedInt32.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

    /**
     * Encodes the specified RepeatedInt32 message, length delimited. Does not implicitly {@link RepeatedInt32.verify|verify} messages.
     * @param message RepeatedInt32 message or plain object to encode
     * @param [writer] Writer to encode to
     * @returns Writer
     */
    static encodeDelimited(message: RepeatedInt32.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

    /**
     * Decodes a RepeatedInt32 message from the specified reader or buffer.
     * @param reader Reader or buffer to decode from
     * @param [length] Message length if known beforehand
     * @returns {RepeatedInt32 & RepeatedInt32.$Shape} RepeatedInt32
     * @throws {Error} If the payload is not a reader or valid buffer
     * @throws {$protobuf.util.ProtocolError} If required fields are missing
     */
    static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): RepeatedInt32 & RepeatedInt32.$Shape;

    /**
     * Decodes a RepeatedInt32 message from the specified reader or buffer, length delimited.
     * @param reader Reader or buffer to decode from
     * @returns {RepeatedInt32 & RepeatedInt32.$Shape} RepeatedInt32
     * @throws {Error} If the payload is not a reader or valid buffer
     * @throws {$protobuf.util.ProtocolError} If required fields are missing
     */
    static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): RepeatedInt32 & RepeatedInt32.$Shape;

    /**
     * Verifies a RepeatedInt32 message.
     * @param message Plain object to verify
     * @returns `null` if valid, otherwise the reason why it is not
     */
    static verify(message: { [k: string]: any }): (string|null);

    /**
     * Creates a RepeatedInt32 message from a plain object. Also converts values to their respective internal types.
     * @param object Plain object
     * @returns RepeatedInt32
     */
    static fromObject(object: { [k: string]: any }): RepeatedInt32;

    /**
     * Creates a plain object from a RepeatedInt32 message. Also converts values to other types if specified.
     * @param message RepeatedInt32
     * @param [options] Conversion options
     * @returns Plain object
     */
    static toObject(message: RepeatedInt32, options?: $protobuf.IConversionOptions): { [k: string]: any };

    /**
     * Converts this RepeatedInt32 to JSON.
     * @returns JSON object
     */
    toJSON(): { [k: string]: any };

    /**
     * Gets the type url for RepeatedInt32
     * @param [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
     * @returns The type url
     */
    static getTypeUrl(prefix?: string): string;
}

export namespace RepeatedInt32 {

    /** Properties of a RepeatedInt32. */
    interface $Properties {

        /** RepeatedInt32 numbers */
        numbers?: (number[]|null);

        /** Unknown fields preserved while decoding when enabled */
        $unknowns?: Uint8Array[];
    }

    /** Shape of a RepeatedInt32. */
    type $Shape = RepeatedInt32.$Properties;
}

/**
 * Properties of a RepeatedInt64.
 * @deprecated Use RepeatedInt64.$Properties instead.
 */
export interface IRepeatedInt64 extends RepeatedInt64.$Properties {
}

/** Represents a RepeatedInt64. */
export class RepeatedInt64 {

    /**
     * Constructs a new RepeatedInt64.
     * @param [properties] Properties to set
     */
    constructor(properties?: RepeatedInt64.$Properties);

    /** Unknown fields preserved while decoding when enabled */
    $unknowns?: Uint8Array[];

    /** RepeatedInt64 numbers. */
    numbers: (number|Long)[];

    /**
     * Creates a new RepeatedInt64 instance using the specified properties.
     * @param [properties] Properties to set
     * @returns RepeatedInt64 instance
     */
    static create(properties: RepeatedInt64.$Shape): RepeatedInt64 & RepeatedInt64.$Shape;
    static create(properties?: RepeatedInt64.$Properties): RepeatedInt64;

    /**
     * Encodes the specified RepeatedInt64 message. Does not implicitly {@link RepeatedInt64.verify|verify} messages.
     * @param message RepeatedInt64 message or plain object to encode
     * @param [writer] Writer to encode to
     * @returns Writer
     */
    static encode(message: RepeatedInt64.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

    /**
     * Encodes the specified RepeatedInt64 message, length delimited. Does not implicitly {@link RepeatedInt64.verify|verify} messages.
     * @param message RepeatedInt64 message or plain object to encode
     * @param [writer] Writer to encode to
     * @returns Writer
     */
    static encodeDelimited(message: RepeatedInt64.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

    /**
     * Decodes a RepeatedInt64 message from the specified reader or buffer.
     * @param reader Reader or buffer to decode from
     * @param [length] Message length if known beforehand
     * @returns {RepeatedInt64 & RepeatedInt64.$Shape} RepeatedInt64
     * @throws {Error} If the payload is not a reader or valid buffer
     * @throws {$protobuf.util.ProtocolError} If required fields are missing
     */
    static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): RepeatedInt64 & RepeatedInt64.$Shape;

    /**
     * Decodes a RepeatedInt64 message from the specified reader or buffer, length delimited.
     * @param reader Reader or buffer to decode from
     * @returns {RepeatedInt64 & RepeatedInt64.$Shape} RepeatedInt64
     * @throws {Error} If the payload is not a reader or valid buffer
     * @throws {$protobuf.util.ProtocolError} If required fields are missing
     */
    static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): RepeatedInt64 & RepeatedInt64.$Shape;

    /**
     * Verifies a RepeatedInt64 message.
     * @param message Plain object to verify
     * @returns `null` if valid, otherwise the reason why it is not
     */
    static verify(message: { [k: string]: any }): (string|null);

    /**
     * Creates a RepeatedInt64 message from a plain object. Also converts values to their respective internal types.
     * @param object Plain object
     * @returns RepeatedInt64
     */
    static fromObject(object: { [k: string]: any }): RepeatedInt64;

    /**
     * Creates a plain object from a RepeatedInt64 message. Also converts values to other types if specified.
     * @param message RepeatedInt64
     * @param [options] Conversion options
     * @returns Plain object
     */
    static toObject(message: RepeatedInt64, options?: $protobuf.IConversionOptions): { [k: string]: any };

    /**
     * Converts this RepeatedInt64 to JSON.
     * @returns JSON object
     */
    toJSON(): { [k: string]: any };

    /**
     * Gets the type url for RepeatedInt64
     * @param [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
     * @returns The type url
     */
    static getTypeUrl(prefix?: string): string;
}

export namespace RepeatedInt64 {

    /** Properties of a RepeatedInt64. */
    interface $Properties {

        /** RepeatedInt64 numbers */
        numbers?: ((number|Long)[]|null);

        /** Unknown fields preserved while decoding when enabled */
        $unknowns?: Uint8Array[];
    }

    /** Shape of a RepeatedInt64. */
    type $Shape = RepeatedInt64.$Properties;
}

/**
 * Properties of a RepeatedFloat.
 * @deprecated Use RepeatedFloat.$Properties instead.
 */
export interface IRepeatedFloat extends RepeatedFloat.$Properties {
}

/** Represents a RepeatedFloat. */
export class RepeatedFloat {

    /**
     * Constructs a new RepeatedFloat.
     * @param [properties] Properties to set
     */
    constructor(properties?: RepeatedFloat.$Properties);

    /** Unknown fields preserved while decoding when enabled */
    $unknowns?: Uint8Array[];

    /** RepeatedFloat numbers. */
    numbers: number[];

    /**
     * Creates a new RepeatedFloat instance using the specified properties.
     * @param [properties] Properties to set
     * @returns RepeatedFloat instance
     */
    static create(properties: RepeatedFloat.$Shape): RepeatedFloat & RepeatedFloat.$Shape;
    static create(properties?: RepeatedFloat.$Properties): RepeatedFloat;

    /**
     * Encodes the specified RepeatedFloat message. Does not implicitly {@link RepeatedFloat.verify|verify} messages.
     * @param message RepeatedFloat message or plain object to encode
     * @param [writer] Writer to encode to
     * @returns Writer
     */
    static encode(message: RepeatedFloat.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

    /**
     * Encodes the specified RepeatedFloat message, length delimited. Does not implicitly {@link RepeatedFloat.verify|verify} messages.
     * @param message RepeatedFloat message or plain object to encode
     * @param [writer] Writer to encode to
     * @returns Writer
     */
    static encodeDelimited(message: RepeatedFloat.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

    /**
     * Decodes a RepeatedFloat message from the specified reader or buffer.
     * @param reader Reader or buffer to decode from
     * @param [length] Message length if known beforehand
     * @returns {RepeatedFloat & RepeatedFloat.$Shape} RepeatedFloat
     * @throws {Error} If the payload is not a reader or valid buffer
     * @throws {$protobuf.util.ProtocolError} If required fields are missing
     */
    static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): RepeatedFloat & RepeatedFloat.$Shape;

    /**
     * Decodes a RepeatedFloat message from the specified reader or buffer, length delimited.
     * @param reader Reader or buffer to decode from
     * @returns {RepeatedFloat & RepeatedFloat.$Shape} RepeatedFloat
     * @throws {Error} If the payload is not a reader or valid buffer
     * @throws {$protobuf.util.ProtocolError} If required fields are missing
     */
    static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): RepeatedFloat & RepeatedFloat.$Shape;

    /**
     * Verifies a RepeatedFloat message.
     * @param message Plain object to verify
     * @returns `null` if valid, otherwise the reason why it is not
     */
    static verify(message: { [k: string]: any }): (string|null);

    /**
     * Creates a RepeatedFloat message from a plain object. Also converts values to their respective internal types.
     * @param object Plain object
     * @returns RepeatedFloat
     */
    static fromObject(object: { [k: string]: any }): RepeatedFloat;

    /**
     * Creates a plain object from a RepeatedFloat message. Also converts values to other types if specified.
     * @param message RepeatedFloat
     * @param [options] Conversion options
     * @returns Plain object
     */
    static toObject(message: RepeatedFloat, options?: $protobuf.IConversionOptions): { [k: string]: any };

    /**
     * Converts this RepeatedFloat to JSON.
     * @returns JSON object
     */
    toJSON(): { [k: string]: any };

    /**
     * Gets the type url for RepeatedFloat
     * @param [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
     * @returns The type url
     */
    static getTypeUrl(prefix?: string): string;
}

export namespace RepeatedFloat {

    /** Properties of a RepeatedFloat. */
    interface $Properties {

        /** RepeatedFloat numbers */
        numbers?: (number[]|null);

        /** Unknown fields preserved while decoding when enabled */
        $unknowns?: Uint8Array[];
    }

    /** Shape of a RepeatedFloat. */
    type $Shape = RepeatedFloat.$Properties;
}

/**
 * Properties of a RepeatedDouble.
 * @deprecated Use RepeatedDouble.$Properties instead.
 */
export interface IRepeatedDouble extends RepeatedDouble.$Properties {
}

/** Represents a RepeatedDouble. */
export class RepeatedDouble {

    /**
     * Constructs a new RepeatedDouble.
     * @param [properties] Properties to set
     */
    constructor(properties?: RepeatedDouble.$Properties);

    /** Unknown fields preserved while decoding when enabled */
    $unknowns?: Uint8Array[];

    /** RepeatedDouble numbers. */
    numbers: number[];

    /**
     * Creates a new RepeatedDouble instance using the specified properties.
     * @param [properties] Properties to set
     * @returns RepeatedDouble instance
     */
    static create(properties: RepeatedDouble.$Shape): RepeatedDouble & RepeatedDouble.$Shape;
    static create(properties?: RepeatedDouble.$Properties): RepeatedDouble;

    /**
     * Encodes the specified RepeatedDouble message. Does not implicitly {@link RepeatedDouble.verify|verify} messages.
     * @param message RepeatedDouble message or plain object to encode
     * @param [writer] Writer to encode to
     * @returns Writer
     */
    static encode(message: RepeatedDouble.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

    /**
     * Encodes the specified RepeatedDouble message, length delimited. Does not implicitly {@link RepeatedDouble.verify|verify} messages.
     * @param message RepeatedDouble message or plain object to encode
     * @param [writer] Writer to encode to
     * @returns Writer
     */
    static encodeDelimited(message: RepeatedDouble.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

    /**
     * Decodes a RepeatedDouble message from the specified reader or buffer.
     * @param reader Reader or buffer to decode from
     * @param [length] Message length if known beforehand
     * @returns {RepeatedDouble & RepeatedDouble.$Shape} RepeatedDouble
     * @throws {Error} If the payload is not a reader or valid buffer
     * @throws {$protobuf.util.ProtocolError} If required fields are missing
     */
    static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): RepeatedDouble & RepeatedDouble.$Shape;

    /**
     * Decodes a RepeatedDouble message from the specified reader or buffer, length delimited.
     * @param reader Reader or buffer to decode from
     * @returns {RepeatedDouble & RepeatedDouble.$Shape} RepeatedDouble
     * @throws {Error} If the payload is not a reader or valid buffer
     * @throws {$protobuf.util.ProtocolError} If required fields are missing
     */
    static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): RepeatedDouble & RepeatedDouble.$Shape;

    /**
     * Verifies a RepeatedDouble message.
     * @param message Plain object to verify
     * @returns `null` if valid, otherwise the reason why it is not
     */
    static verify(message: { [k: string]: any }): (string|null);

    /**
     * Creates a RepeatedDouble message from a plain object. Also converts values to their respective internal types.
     * @param object Plain object
     * @returns RepeatedDouble
     */
    static fromObject(object: { [k: string]: any }): RepeatedDouble;

    /**
     * Creates a plain object from a RepeatedDouble message. Also converts values to other types if specified.
     * @param message RepeatedDouble
     * @param [options] Conversion options
     * @returns Plain object
     */
    static toObject(message: RepeatedDouble, options?: $protobuf.IConversionOptions): { [k: string]: any };

    /**
     * Converts this RepeatedDouble to JSON.
     * @returns JSON object
     */
    toJSON(): { [k: string]: any };

    /**
     * Gets the type url for RepeatedDouble
     * @param [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
     * @returns The type url
     */
    static getTypeUrl(prefix?: string): string;
}

export namespace RepeatedDouble {

    /** Properties of a RepeatedDouble. */
    interface $Properties {

        /** RepeatedDouble numbers */
        numbers?: (number[]|null);

        /** Unknown fields preserved while decoding when enabled */
        $unknowns?: Uint8Array[];
    }

    /** Shape of a RepeatedDouble. */
    type $Shape = RepeatedDouble.$Properties;
}

/**
 * Properties of an ElementMessage.
 * @deprecated Use ElementMessage.$Properties instead.
 */
export interface IElementMessage extends ElementMessage.$Properties {
}

/** Represents an ElementMessage. */
export class ElementMessage {

    /**
     * Constructs a new ElementMessage.
     * @param [properties] Properties to set
     */
    constructor(properties?: ElementMessage.$Properties);

    /** Unknown fields preserved while decoding when enabled */
    $unknowns?: Uint8Array[];

    /** ElementMessage type. */
    type: string;

    /** ElementMessage id. */
    id: Uint8Array;

    /** ElementMessage objectId. */
    objectId: string;

    /** ElementMessage source. */
    source: Uint8Array;

    /** ElementMessage displayName. */
    displayName: string;

    /** ElementMessage positions. */
    positions: number[];

    /** ElementMessage stale. */
    stale: (number|Long);

    /** ElementMessage start. */
    start: (number|Long);

    /** ElementMessage time. */
    time: (number|Long);

    /** ElementMessage values. */
    values: { [k: string]: Value.$Properties };

    /**
     * Creates a new ElementMessage instance using the specified properties.
     * @param [properties] Properties to set
     * @returns ElementMessage instance
     */
    static create(properties: ElementMessage.$Shape): ElementMessage & ElementMessage.$Shape;
    static create(properties?: ElementMessage.$Properties): ElementMessage;

    /**
     * Encodes the specified ElementMessage message. Does not implicitly {@link ElementMessage.verify|verify} messages.
     * @param message ElementMessage message or plain object to encode
     * @param [writer] Writer to encode to
     * @returns Writer
     */
    static encode(message: ElementMessage.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

    /**
     * Encodes the specified ElementMessage message, length delimited. Does not implicitly {@link ElementMessage.verify|verify} messages.
     * @param message ElementMessage message or plain object to encode
     * @param [writer] Writer to encode to
     * @returns Writer
     */
    static encodeDelimited(message: ElementMessage.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

    /**
     * Decodes an ElementMessage message from the specified reader or buffer.
     * @param reader Reader or buffer to decode from
     * @param [length] Message length if known beforehand
     * @returns {ElementMessage & ElementMessage.$Shape} ElementMessage
     * @throws {Error} If the payload is not a reader or valid buffer
     * @throws {$protobuf.util.ProtocolError} If required fields are missing
     */
    static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): ElementMessage & ElementMessage.$Shape;

    /**
     * Decodes an ElementMessage message from the specified reader or buffer, length delimited.
     * @param reader Reader or buffer to decode from
     * @returns {ElementMessage & ElementMessage.$Shape} ElementMessage
     * @throws {Error} If the payload is not a reader or valid buffer
     * @throws {$protobuf.util.ProtocolError} If required fields are missing
     */
    static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): ElementMessage & ElementMessage.$Shape;

    /**
     * Verifies an ElementMessage message.
     * @param message Plain object to verify
     * @returns `null` if valid, otherwise the reason why it is not
     */
    static verify(message: { [k: string]: any }): (string|null);

    /**
     * Creates an ElementMessage message from a plain object. Also converts values to their respective internal types.
     * @param object Plain object
     * @returns ElementMessage
     */
    static fromObject(object: { [k: string]: any }): ElementMessage;

    /**
     * Creates a plain object from an ElementMessage message. Also converts values to other types if specified.
     * @param message ElementMessage
     * @param [options] Conversion options
     * @returns Plain object
     */
    static toObject(message: ElementMessage, options?: $protobuf.IConversionOptions): { [k: string]: any };

    /**
     * Converts this ElementMessage to JSON.
     * @returns JSON object
     */
    toJSON(): { [k: string]: any };

    /**
     * Gets the type url for ElementMessage
     * @param [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
     * @returns The type url
     */
    static getTypeUrl(prefix?: string): string;
}

export namespace ElementMessage {

    /** Properties of an ElementMessage. */
    interface $Properties {

        /** ElementMessage type */
        type?: (string|null);

        /** ElementMessage id */
        id?: (Uint8Array|null);

        /** ElementMessage objectId */
        objectId?: (string|null);

        /** ElementMessage source */
        source?: (Uint8Array|null);

        /** ElementMessage displayName */
        displayName?: (string|null);

        /** ElementMessage positions */
        positions?: (number[]|null);

        /** ElementMessage stale */
        stale?: (number|Long|null);

        /** ElementMessage start */
        start?: (number|Long|null);

        /** ElementMessage time */
        time?: (number|Long|null);

        /** ElementMessage values */
        values?: ({ [k: string]: Value.$Properties }|null);

        /** Unknown fields preserved while decoding when enabled */
        $unknowns?: Uint8Array[];
    }

    /** Shape of an ElementMessage. */
    type $Shape = {
      type?: string|null;
      id?: Uint8Array|null;
      objectId?: string|null;
      source?: Uint8Array|null;
      displayName?: string|null;
      positions?: number[]|null;
      stale?: number|Long|null;
      start?: number|Long|null;
      time?: number|Long|null;
      values?: { [k: string]: Value.$Shape }|null;
      $unknowns?: Uint8Array[];
    };
}
