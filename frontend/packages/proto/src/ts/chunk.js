// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/*eslint-disable block-scoped-var, id-length, no-control-regex, no-magic-numbers, no-mixed-operators, no-prototype-builtins, no-redeclare, no-shadow, no-var, sort-vars, default-case, jsdoc/require-param*/
import $protobuf from "protobufjs/minimal.js";

// Common aliases
const $Reader = $protobuf.Reader, $Writer = $protobuf.Writer, $util = $protobuf.util;
const $Object = $util.global.Object, $undefined = $util.global.undefined, $Error = $util.global.Error, $TypeError = $util.global.TypeError, $parseInt = $util.global.parseInt, $Number = $util.global.Number, $String = $util.global.String, $Boolean = $util.global.Boolean, $BigInt = $util.global.BigInt, $isFinite = $util.global.isFinite, $Array = $util.global.Array;

// Exported root namespace
const $root = $protobuf.roots["default"] || ($protobuf.roots["default"] = {});

export const Value = $root.Value = (() => {

    /**
     * Properties of a Value.
     * @typedef {Object} Value.$Properties
     * @property {number|null} [int_32] Value int_32
     * @property {RepeatedInt32.$Properties|null} [int_32Arr] Value int_32Arr
     * @property {number|Long|null} [int_64] Value int_64
     * @property {RepeatedInt64.$Properties|null} [int_64Arr] Value int_64Arr
     * @property {number|null} [float] Value float
     * @property {RepeatedFloat.$Properties|null} [floatArr] Value floatArr
     * @property {number|null} [double] Value double
     * @property {RepeatedDouble.$Properties|null} [doubleArr] Value doubleArr
     * @property {string|null} [string] Value string
     * @property {boolean|null} [bool] Value bool
     * @property {"int_32"|"int_32Arr"|"int_64"|"int_64Arr"|"float"|"floatArr"|"double"|"doubleArr"|"string"|"bool"} [kind] Value kind
     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
     */

    /**
     * Properties of a Value.
     * @name IValue
     * @interface IValue
     * @augments Value.$Properties
     * @deprecated Use Value.$Properties instead.
     */

    /**
     * Narrowed shape of a Value.
     * @typedef {{
     *   int_32?: number|null;
     *   int_32Arr?: RepeatedInt32.$Shape|null;
     *   int_64?: number|Long|null;
     *   int_64Arr?: RepeatedInt64.$Shape|null;
     *   float?: number|null;
     *   floatArr?: RepeatedFloat.$Shape|null;
     *   double?: number|null;
     *   doubleArr?: RepeatedDouble.$Shape|null;
     *   string?: string|null;
     *   bool?: boolean|null;
     *   $unknowns?: Array.<Uint8Array>;
     * } & (
     *   ({ kind?: undefined; int_32?: null; int_32Arr?: null; int_64?: null; int_64Arr?: null; float?: null; floatArr?: null; double?: null; doubleArr?: null; string?: null; bool?: null }|{ kind?: "int_32"; int_32: number; int_32Arr?: null; int_64?: null; int_64Arr?: null; float?: null; floatArr?: null; double?: null; doubleArr?: null; string?: null; bool?: null }|{ kind?: "int_32Arr"; int_32?: null; int_32Arr: RepeatedInt32.$Shape; int_64?: null; int_64Arr?: null; float?: null; floatArr?: null; double?: null; doubleArr?: null; string?: null; bool?: null }|{ kind?: "int_64"; int_32?: null; int_32Arr?: null; int_64: number|Long; int_64Arr?: null; float?: null; floatArr?: null; double?: null; doubleArr?: null; string?: null; bool?: null }|{ kind?: "int_64Arr"; int_32?: null; int_32Arr?: null; int_64?: null; int_64Arr: RepeatedInt64.$Shape; float?: null; floatArr?: null; double?: null; doubleArr?: null; string?: null; bool?: null }|{ kind?: "float"; int_32?: null; int_32Arr?: null; int_64?: null; int_64Arr?: null; float: number; floatArr?: null; double?: null; doubleArr?: null; string?: null; bool?: null }|{ kind?: "floatArr"; int_32?: null; int_32Arr?: null; int_64?: null; int_64Arr?: null; float?: null; floatArr: RepeatedFloat.$Shape; double?: null; doubleArr?: null; string?: null; bool?: null }|{ kind?: "double"; int_32?: null; int_32Arr?: null; int_64?: null; int_64Arr?: null; float?: null; floatArr?: null; double: number; doubleArr?: null; string?: null; bool?: null }|{ kind?: "doubleArr"; int_32?: null; int_32Arr?: null; int_64?: null; int_64Arr?: null; float?: null; floatArr?: null; double?: null; doubleArr: RepeatedDouble.$Shape; string?: null; bool?: null }|{ kind?: "string"; int_32?: null; int_32Arr?: null; int_64?: null; int_64Arr?: null; float?: null; floatArr?: null; double?: null; doubleArr?: null; string: string; bool?: null }|{ kind?: "bool"; int_32?: null; int_32Arr?: null; int_64?: null; int_64Arr?: null; float?: null; floatArr?: null; double?: null; doubleArr?: null; string?: null; bool: boolean })
     * )} Value.$Shape
     */

    /**
     * Constructs a new Value.
     * @name Value
     * @classdesc Represents a Value.
     * @constructor
     * @param {Value.$Properties=} [properties] Properties to set
     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
     */
    const Value = function (properties) {
        if (properties)
            for (let keys = $Object.keys(properties), i = 0; i < keys.length; ++i)
                if (properties[keys[i]] != null && keys[i] !== "__proto__")
                    this[keys[i]] = properties[keys[i]];
    };

    /**
     * Value int_32.
     * @member {number|null|undefined} int_32
     * @memberof Value
     * @instance
     */
    Value.prototype.int_32 = null;

    /**
     * Value int_32Arr.
     * @member {RepeatedInt32.$Properties|null|undefined} int_32Arr
     * @memberof Value
     * @instance
     */
    Value.prototype.int_32Arr = null;

    /**
     * Value int_64.
     * @member {number|Long|null|undefined} int_64
     * @memberof Value
     * @instance
     */
    Value.prototype.int_64 = null;

    /**
     * Value int_64Arr.
     * @member {RepeatedInt64.$Properties|null|undefined} int_64Arr
     * @memberof Value
     * @instance
     */
    Value.prototype.int_64Arr = null;

    /**
     * Value float.
     * @member {number|null|undefined} float
     * @memberof Value
     * @instance
     */
    Value.prototype.float = null;

    /**
     * Value floatArr.
     * @member {RepeatedFloat.$Properties|null|undefined} floatArr
     * @memberof Value
     * @instance
     */
    Value.prototype.floatArr = null;

    /**
     * Value double.
     * @member {number|null|undefined} double
     * @memberof Value
     * @instance
     */
    Value.prototype.double = null;

    /**
     * Value doubleArr.
     * @member {RepeatedDouble.$Properties|null|undefined} doubleArr
     * @memberof Value
     * @instance
     */
    Value.prototype.doubleArr = null;

    /**
     * Value string.
     * @member {string|null|undefined} string
     * @memberof Value
     * @instance
     */
    Value.prototype.string = null;

    /**
     * Value bool.
     * @member {boolean|null|undefined} bool
     * @memberof Value
     * @instance
     */
    Value.prototype.bool = null;

    // OneOf field names bound to virtual getters and setters
    let $oneOfFields;

    /**
     * Value kind.
     * @member {"int_32"|"int_32Arr"|"int_64"|"int_64Arr"|"float"|"floatArr"|"double"|"doubleArr"|"string"|"bool"|undefined} kind
     * @memberof Value
     * @instance
     */
    $Object.defineProperty(Value.prototype, "kind", {
        get: $util.oneOfGetter($oneOfFields = ["int_32", "int_32Arr", "int_64", "int_64Arr", "float", "floatArr", "double", "doubleArr", "string", "bool"]),
        set: $util.oneOfSetter($oneOfFields)
    });

    /**
     * Creates a new Value instance using the specified properties.
     * @function create
     * @memberof Value
     * @static
     * @param {Value.$Properties=} [properties] Properties to set
     * @returns {Value} Value instance
     * @type {{
     *   (properties: Value.$Shape): Value & Value.$Shape;
     *   (properties?: Value.$Properties): Value;
     * }}
     */
    Value.create = function(properties) {
        return new Value(properties);
    };

    /**
     * Encodes the specified Value message. Does not implicitly {@link Value.verify|verify} messages.
     * @function encode
     * @memberof Value
     * @static
     * @param {Value.$Properties} message Value message or plain object to encode
     * @param {$protobuf.Writer} [writer] Writer to encode to
     * @returns {$protobuf.Writer} Writer
     */
    Value.encode = function (message, writer, _depth) {
        if (!writer)
            writer = $Writer.create();
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $util.recursionLimit)
            throw $Error("max depth exceeded");
        if (message.int_32 != null && $Object.hasOwnProperty.call(message, "int_32"))
            writer.uint32(/* id 1, wireType 0 =*/8).int32(message.int_32);
        if (message.int_32Arr != null && $Object.hasOwnProperty.call(message, "int_32Arr"))
            $root.RepeatedInt32.encode(message.int_32Arr, writer.uint32(/* id 2, wireType 2 =*/18).fork(), _depth + 1).ldelim();
        if (message.int_64 != null && $Object.hasOwnProperty.call(message, "int_64"))
            writer.uint32(/* id 3, wireType 0 =*/24).int64(message.int_64);
        if (message.int_64Arr != null && $Object.hasOwnProperty.call(message, "int_64Arr"))
            $root.RepeatedInt64.encode(message.int_64Arr, writer.uint32(/* id 4, wireType 2 =*/34).fork(), _depth + 1).ldelim();
        if (message.float != null && $Object.hasOwnProperty.call(message, "float"))
            writer.uint32(/* id 5, wireType 5 =*/45).float(message.float);
        if (message.floatArr != null && $Object.hasOwnProperty.call(message, "floatArr"))
            $root.RepeatedFloat.encode(message.floatArr, writer.uint32(/* id 6, wireType 2 =*/50).fork(), _depth + 1).ldelim();
        if (message.double != null && $Object.hasOwnProperty.call(message, "double"))
            writer.uint32(/* id 7, wireType 1 =*/57).double(message.double);
        if (message.doubleArr != null && $Object.hasOwnProperty.call(message, "doubleArr"))
            $root.RepeatedDouble.encode(message.doubleArr, writer.uint32(/* id 8, wireType 2 =*/66).fork(), _depth + 1).ldelim();
        if (message.string != null && $Object.hasOwnProperty.call(message, "string"))
            writer.uint32(/* id 9, wireType 2 =*/74).string(message.string);
        if (message.bool != null && $Object.hasOwnProperty.call(message, "bool"))
            writer.uint32(/* id 10, wireType 0 =*/80).bool(message.bool);
        if (message.$unknowns != null && $Object.hasOwnProperty.call(message, "$unknowns"))
            for (let i = 0; i < message.$unknowns.length; ++i)
                writer.raw(message.$unknowns[i]);
        return writer;
    };

    /**
     * Encodes the specified Value message, length delimited. Does not implicitly {@link Value.verify|verify} messages.
     * @function encodeDelimited
     * @memberof Value
     * @static
     * @param {Value.$Properties} message Value message or plain object to encode
     * @param {$protobuf.Writer} [writer] Writer to encode to
     * @returns {$protobuf.Writer} Writer
     */
    Value.encodeDelimited = function(message, writer) {
        return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
    };

    /**
     * Decodes a Value message from the specified reader or buffer.
     * @function decode
     * @memberof Value
     * @static
     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
     * @param {number} [length] Message length if known beforehand
     * @returns {Value & Value.$Shape} Value
     * @throws {Error} If the payload is not a reader or valid buffer
     * @throws {$protobuf.util.ProtocolError} If required fields are missing
     */
    Value.decode = function (reader, length, _end, _depth, _target) {
        if (!(reader instanceof $Reader))
            reader = $Reader.create(reader);
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $Reader.recursionLimit)
            throw $Error("max depth exceeded");
        let end = length === $undefined ? reader.len : reader.pos + length, message = _target || new $root.Value();
        while (reader.pos < end) {
            let start = reader.pos;
            let tag = reader.tag();
            if (tag === _end) {
                _end = $undefined;
                break;
            }
            let wireType = tag & 7;
            switch (tag >>>= 3) {
            case 1: {
                    if (wireType !== 0)
                        break;
                    message.int_32 = reader.int32();
                    message.kind = "int_32";
                    continue;
                }
            case 2: {
                    if (wireType !== 2)
                        break;
                    message.int_32Arr = $root.RepeatedInt32.decode(reader, reader.uint32(), $undefined, _depth + 1, message.int_32Arr);
                    message.kind = "int_32Arr";
                    continue;
                }
            case 3: {
                    if (wireType !== 0)
                        break;
                    message.int_64 = reader.int64();
                    message.kind = "int_64";
                    continue;
                }
            case 4: {
                    if (wireType !== 2)
                        break;
                    message.int_64Arr = $root.RepeatedInt64.decode(reader, reader.uint32(), $undefined, _depth + 1, message.int_64Arr);
                    message.kind = "int_64Arr";
                    continue;
                }
            case 5: {
                    if (wireType !== 5)
                        break;
                    message.float = reader.float();
                    message.kind = "float";
                    continue;
                }
            case 6: {
                    if (wireType !== 2)
                        break;
                    message.floatArr = $root.RepeatedFloat.decode(reader, reader.uint32(), $undefined, _depth + 1, message.floatArr);
                    message.kind = "floatArr";
                    continue;
                }
            case 7: {
                    if (wireType !== 1)
                        break;
                    message.double = reader.double();
                    message.kind = "double";
                    continue;
                }
            case 8: {
                    if (wireType !== 2)
                        break;
                    message.doubleArr = $root.RepeatedDouble.decode(reader, reader.uint32(), $undefined, _depth + 1, message.doubleArr);
                    message.kind = "doubleArr";
                    continue;
                }
            case 9: {
                    if (wireType !== 2)
                        break;
                    message.string = reader.stringVerify();
                    message.kind = "string";
                    continue;
                }
            case 10: {
                    if (wireType !== 0)
                        break;
                    message.bool = reader.bool();
                    message.kind = "bool";
                    continue;
                }
            }
            reader.skipType(wireType, _depth, tag);
            if (!reader.discardUnknown) {
                $util.makeProp(message, "$unknowns", false);
                (message.$unknowns || (message.$unknowns = [])).push(reader.raw(start, reader.pos));
            }
        }
        if (_end !== $undefined)
            throw $Error("missing end group");
        return message;
    };

    /**
     * Decodes a Value message from the specified reader or buffer, length delimited.
     * @function decodeDelimited
     * @memberof Value
     * @static
     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
     * @returns {Value & Value.$Shape} Value
     * @throws {Error} If the payload is not a reader or valid buffer
     * @throws {$protobuf.util.ProtocolError} If required fields are missing
     */
    Value.decodeDelimited = function(reader) {
        if (!(reader instanceof $Reader))
            reader = new $Reader(reader);
        return this.decode(reader, reader.uint32());
    };

    /**
     * Verifies a Value message.
     * @function verify
     * @memberof Value
     * @static
     * @param {Object.<string,*>} message Plain object to verify
     * @returns {string|null} `null` if valid, otherwise the reason why it is not
     */
    Value.verify = function (message, _depth) {
        if (typeof message !== "object" || message === null)
            return "object expected";
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $util.recursionLimit)
            return "max depth exceeded";
        let properties = {};
        if (message.int_32 != null && $Object.hasOwnProperty.call(message, "int_32")) {
            properties.kind = 1;
            if (!$util.isInteger(message.int_32))
                return "int_32: integer expected";
        }
        if (message.int_32Arr != null && $Object.hasOwnProperty.call(message, "int_32Arr")) {
            if (properties.kind === 1)
                return "kind: multiple values";
            properties.kind = 1;
            {
                let error = $root.RepeatedInt32.verify(message.int_32Arr, _depth + 1);
                if (error)
                    return "int_32Arr." + error;
            }
        }
        if (message.int_64 != null && $Object.hasOwnProperty.call(message, "int_64")) {
            if (properties.kind === 1)
                return "kind: multiple values";
            properties.kind = 1;
            if (!$util.isInteger(message.int_64) && !(message.int_64 && $util.isInteger(message.int_64.low) && $util.isInteger(message.int_64.high)))
                return "int_64: integer|Long expected";
        }
        if (message.int_64Arr != null && $Object.hasOwnProperty.call(message, "int_64Arr")) {
            if (properties.kind === 1)
                return "kind: multiple values";
            properties.kind = 1;
            {
                let error = $root.RepeatedInt64.verify(message.int_64Arr, _depth + 1);
                if (error)
                    return "int_64Arr." + error;
            }
        }
        if (message.float != null && $Object.hasOwnProperty.call(message, "float")) {
            if (properties.kind === 1)
                return "kind: multiple values";
            properties.kind = 1;
            if (typeof message.float !== "number")
                return "float: number expected";
        }
        if (message.floatArr != null && $Object.hasOwnProperty.call(message, "floatArr")) {
            if (properties.kind === 1)
                return "kind: multiple values";
            properties.kind = 1;
            {
                let error = $root.RepeatedFloat.verify(message.floatArr, _depth + 1);
                if (error)
                    return "floatArr." + error;
            }
        }
        if (message.double != null && $Object.hasOwnProperty.call(message, "double")) {
            if (properties.kind === 1)
                return "kind: multiple values";
            properties.kind = 1;
            if (typeof message.double !== "number")
                return "double: number expected";
        }
        if (message.doubleArr != null && $Object.hasOwnProperty.call(message, "doubleArr")) {
            if (properties.kind === 1)
                return "kind: multiple values";
            properties.kind = 1;
            {
                let error = $root.RepeatedDouble.verify(message.doubleArr, _depth + 1);
                if (error)
                    return "doubleArr." + error;
            }
        }
        if (message.string != null && $Object.hasOwnProperty.call(message, "string")) {
            if (properties.kind === 1)
                return "kind: multiple values";
            properties.kind = 1;
            if (!$util.isString(message.string))
                return "string: string expected";
        }
        if (message.bool != null && $Object.hasOwnProperty.call(message, "bool")) {
            if (properties.kind === 1)
                return "kind: multiple values";
            properties.kind = 1;
            if (typeof message.bool !== "boolean")
                return "bool: boolean expected";
        }
        return null;
    };

    /**
     * Creates a Value message from a plain object. Also converts values to their respective internal types.
     * @function fromObject
     * @memberof Value
     * @static
     * @param {Object.<string,*>} object Plain object
     * @returns {Value} Value
     */
    Value.fromObject = function (object, _depth) {
        if (object instanceof $root.Value)
            return object;
        if (!$util.isObject(object))
            throw $TypeError(".Value: object expected");
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $util.recursionLimit)
            throw $Error("max depth exceeded");
        let message = new $root.Value();
        if (object.int_32 != null)
            message.int_32 = object.int_32 | 0;
        if (object.int_32Arr != null) {
            if (!$util.isObject(object.int_32Arr))
                throw $TypeError(".Value.int_32Arr: object expected");
            message.int_32Arr = $root.RepeatedInt32.fromObject(object.int_32Arr, _depth + 1);
        }
        if (object.int_64 != null)
            if ($util.Long)
                message.int_64 = $util.Long.fromValue(object.int_64, false);
            else if (typeof object.int_64 === "string")
                message.int_64 = $parseInt(object.int_64, 10);
            else if (typeof object.int_64 === "number")
                message.int_64 = object.int_64;
            else if (typeof object.int_64 === "object")
                message.int_64 = new $util.LongBits(object.int_64.low >>> 0, object.int_64.high >>> 0).toNumber();
        if (object.int_64Arr != null) {
            if (!$util.isObject(object.int_64Arr))
                throw $TypeError(".Value.int_64Arr: object expected");
            message.int_64Arr = $root.RepeatedInt64.fromObject(object.int_64Arr, _depth + 1);
        }
        if (object.float != null)
            message.float = $Number(object.float);
        if (object.floatArr != null) {
            if (!$util.isObject(object.floatArr))
                throw $TypeError(".Value.floatArr: object expected");
            message.floatArr = $root.RepeatedFloat.fromObject(object.floatArr, _depth + 1);
        }
        if (object.double != null)
            message.double = $Number(object.double);
        if (object.doubleArr != null) {
            if (!$util.isObject(object.doubleArr))
                throw $TypeError(".Value.doubleArr: object expected");
            message.doubleArr = $root.RepeatedDouble.fromObject(object.doubleArr, _depth + 1);
        }
        if (object.string != null)
            message.string = $String(object.string);
        if (object.bool != null)
            message.bool = $Boolean(object.bool);
        return message;
    };

    /**
     * Creates a plain object from a Value message. Also converts values to other types if specified.
     * @function toObject
     * @memberof Value
     * @static
     * @param {Value} message Value
     * @param {$protobuf.IConversionOptions} [options] Conversion options
     * @returns {Object.<string,*>} Plain object
     */
    Value.toObject = function (message, options, _depth) {
        if (!options)
            options = {};
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $util.recursionLimit)
            throw $Error("max depth exceeded");
        let object = {};
        if (message.int_32 != null && $Object.hasOwnProperty.call(message, "int_32")) {
            object.int_32 = message.int_32;
            if (options.oneofs)
                object.kind = "int_32";
        }
        if (message.int_32Arr != null && $Object.hasOwnProperty.call(message, "int_32Arr")) {
            object.int_32Arr = $root.RepeatedInt32.toObject(message.int_32Arr, options, _depth + 1);
            if (options.oneofs)
                object.kind = "int_32Arr";
        }
        if (message.int_64 != null && $Object.hasOwnProperty.call(message, "int_64")) {
            if (typeof $BigInt !== "undefined" && options.longs === $BigInt)
                object.int_64 = typeof message.int_64 === "number" ? $BigInt(message.int_64) : $util.Long.fromBits(message.int_64.low >>> 0, message.int_64.high >>> 0, false).toBigInt();
            else if (typeof message.int_64 === "number")
                object.int_64 = options.longs === $String ? $String(message.int_64) : message.int_64;
            else
                object.int_64 = options.longs === $String ? $util.Long.prototype.toString.call(message.int_64) : options.longs === $Number ? new $util.LongBits(message.int_64.low >>> 0, message.int_64.high >>> 0).toNumber() : message.int_64;
            if (options.oneofs)
                object.kind = "int_64";
        }
        if (message.int_64Arr != null && $Object.hasOwnProperty.call(message, "int_64Arr")) {
            object.int_64Arr = $root.RepeatedInt64.toObject(message.int_64Arr, options, _depth + 1);
            if (options.oneofs)
                object.kind = "int_64Arr";
        }
        if (message.float != null && $Object.hasOwnProperty.call(message, "float")) {
            object.float = options.json && !$isFinite(message.float) ? $String(message.float) : message.float;
            if (options.oneofs)
                object.kind = "float";
        }
        if (message.floatArr != null && $Object.hasOwnProperty.call(message, "floatArr")) {
            object.floatArr = $root.RepeatedFloat.toObject(message.floatArr, options, _depth + 1);
            if (options.oneofs)
                object.kind = "floatArr";
        }
        if (message.double != null && $Object.hasOwnProperty.call(message, "double")) {
            object.double = options.json && !$isFinite(message.double) ? $String(message.double) : message.double;
            if (options.oneofs)
                object.kind = "double";
        }
        if (message.doubleArr != null && $Object.hasOwnProperty.call(message, "doubleArr")) {
            object.doubleArr = $root.RepeatedDouble.toObject(message.doubleArr, options, _depth + 1);
            if (options.oneofs)
                object.kind = "doubleArr";
        }
        if (message.string != null && $Object.hasOwnProperty.call(message, "string")) {
            object.string = message.string;
            if (options.oneofs)
                object.kind = "string";
        }
        if (message.bool != null && $Object.hasOwnProperty.call(message, "bool")) {
            object.bool = message.bool;
            if (options.oneofs)
                object.kind = "bool";
        }
        return object;
    };

    /**
     * Converts this Value to JSON.
     * @function toJSON
     * @memberof Value
     * @instance
     * @returns {Object.<string,*>} JSON object
     */
    Value.prototype.toJSON = function() {
        return Value.toObject(this, $protobuf.util.toJSONOptions);
    };

    /**
     * Gets the type url for Value
     * @function getTypeUrl
     * @memberof Value
     * @static
     * @param {string} [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
     * @returns {string} The type url
     */
    Value.getTypeUrl = function(prefix) {
        if (prefix === $undefined)
            prefix = "type.googleapis.com";
        return prefix + "/Value";
    };

    return Value;
})();

export const RepeatedInt32 = $root.RepeatedInt32 = (() => {

    /**
     * Properties of a RepeatedInt32.
     * @typedef {Object} RepeatedInt32.$Properties
     * @property {Array.<number>|null} [numbers] RepeatedInt32 numbers
     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
     */

    /**
     * Properties of a RepeatedInt32.
     * @name IRepeatedInt32
     * @interface IRepeatedInt32
     * @augments RepeatedInt32.$Properties
     * @deprecated Use RepeatedInt32.$Properties instead.
     */

    /**
     * Shape of a RepeatedInt32.
     * @typedef {RepeatedInt32.$Properties} RepeatedInt32.$Shape
     */

    /**
     * Constructs a new RepeatedInt32.
     * @name RepeatedInt32
     * @classdesc Represents a RepeatedInt32.
     * @constructor
     * @param {RepeatedInt32.$Properties=} [properties] Properties to set
     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
     */
    const RepeatedInt32 = function (properties) {
        this.numbers = [];
        if (properties)
            for (let keys = $Object.keys(properties), i = 0; i < keys.length; ++i)
                if (properties[keys[i]] != null && keys[i] !== "__proto__")
                    this[keys[i]] = properties[keys[i]];
    };

    /**
     * RepeatedInt32 numbers.
     * @member {Array.<number>} numbers
     * @memberof RepeatedInt32
     * @instance
     */
    RepeatedInt32.prototype.numbers = $util.emptyArray;

    /**
     * Creates a new RepeatedInt32 instance using the specified properties.
     * @function create
     * @memberof RepeatedInt32
     * @static
     * @param {RepeatedInt32.$Properties=} [properties] Properties to set
     * @returns {RepeatedInt32} RepeatedInt32 instance
     * @type {{
     *   (properties: RepeatedInt32.$Shape): RepeatedInt32 & RepeatedInt32.$Shape;
     *   (properties?: RepeatedInt32.$Properties): RepeatedInt32;
     * }}
     */
    RepeatedInt32.create = function(properties) {
        return new RepeatedInt32(properties);
    };

    /**
     * Encodes the specified RepeatedInt32 message. Does not implicitly {@link RepeatedInt32.verify|verify} messages.
     * @function encode
     * @memberof RepeatedInt32
     * @static
     * @param {RepeatedInt32.$Properties} message RepeatedInt32 message or plain object to encode
     * @param {$protobuf.Writer} [writer] Writer to encode to
     * @returns {$protobuf.Writer} Writer
     */
    RepeatedInt32.encode = function (message, writer, _depth) {
        if (!writer)
            writer = $Writer.create();
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $util.recursionLimit)
            throw $Error("max depth exceeded");
        if (message.numbers != null && message.numbers.length) {
            writer.uint32(/* id 1, wireType 2 =*/10).fork();
            for (let i = 0; i < message.numbers.length; ++i)
                writer.int32(message.numbers[i]);
            writer.ldelim();
        }
        if (message.$unknowns != null && $Object.hasOwnProperty.call(message, "$unknowns"))
            for (let i = 0; i < message.$unknowns.length; ++i)
                writer.raw(message.$unknowns[i]);
        return writer;
    };

    /**
     * Encodes the specified RepeatedInt32 message, length delimited. Does not implicitly {@link RepeatedInt32.verify|verify} messages.
     * @function encodeDelimited
     * @memberof RepeatedInt32
     * @static
     * @param {RepeatedInt32.$Properties} message RepeatedInt32 message or plain object to encode
     * @param {$protobuf.Writer} [writer] Writer to encode to
     * @returns {$protobuf.Writer} Writer
     */
    RepeatedInt32.encodeDelimited = function(message, writer) {
        return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
    };

    /**
     * Decodes a RepeatedInt32 message from the specified reader or buffer.
     * @function decode
     * @memberof RepeatedInt32
     * @static
     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
     * @param {number} [length] Message length if known beforehand
     * @returns {RepeatedInt32 & RepeatedInt32.$Shape} RepeatedInt32
     * @throws {Error} If the payload is not a reader or valid buffer
     * @throws {$protobuf.util.ProtocolError} If required fields are missing
     */
    RepeatedInt32.decode = function (reader, length, _end, _depth, _target) {
        if (!(reader instanceof $Reader))
            reader = $Reader.create(reader);
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $Reader.recursionLimit)
            throw $Error("max depth exceeded");
        let end = length === $undefined ? reader.len : reader.pos + length, message = _target || new $root.RepeatedInt32();
        while (reader.pos < end) {
            let start = reader.pos;
            let tag = reader.tag();
            if (tag === _end) {
                _end = $undefined;
                break;
            }
            let wireType = tag & 7;
            switch (tag >>>= 3) {
            case 1: {
                    if (wireType === 2) {
                        if (!(message.numbers && message.numbers.length))
                            message.numbers = [];
                        let end2 = reader.uint32() + reader.pos;
                        while (reader.pos < end2)
                            message.numbers.push(reader.int32());
                        continue;
                    }
                    if (wireType !== 0)
                        break;
                    if (!(message.numbers && message.numbers.length))
                        message.numbers = [];
                    message.numbers.push(reader.int32());
                    continue;
                }
            }
            reader.skipType(wireType, _depth, tag);
            if (!reader.discardUnknown) {
                $util.makeProp(message, "$unknowns", false);
                (message.$unknowns || (message.$unknowns = [])).push(reader.raw(start, reader.pos));
            }
        }
        if (_end !== $undefined)
            throw $Error("missing end group");
        return message;
    };

    /**
     * Decodes a RepeatedInt32 message from the specified reader or buffer, length delimited.
     * @function decodeDelimited
     * @memberof RepeatedInt32
     * @static
     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
     * @returns {RepeatedInt32 & RepeatedInt32.$Shape} RepeatedInt32
     * @throws {Error} If the payload is not a reader or valid buffer
     * @throws {$protobuf.util.ProtocolError} If required fields are missing
     */
    RepeatedInt32.decodeDelimited = function(reader) {
        if (!(reader instanceof $Reader))
            reader = new $Reader(reader);
        return this.decode(reader, reader.uint32());
    };

    /**
     * Verifies a RepeatedInt32 message.
     * @function verify
     * @memberof RepeatedInt32
     * @static
     * @param {Object.<string,*>} message Plain object to verify
     * @returns {string|null} `null` if valid, otherwise the reason why it is not
     */
    RepeatedInt32.verify = function (message, _depth) {
        if (typeof message !== "object" || message === null)
            return "object expected";
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $util.recursionLimit)
            return "max depth exceeded";
        if (message.numbers != null && $Object.hasOwnProperty.call(message, "numbers")) {
            if (!$Array.isArray(message.numbers))
                return "numbers: array expected";
            for (let i = 0; i < message.numbers.length; ++i)
                if (!$util.isInteger(message.numbers[i]))
                    return "numbers: integer[] expected";
        }
        return null;
    };

    /**
     * Creates a RepeatedInt32 message from a plain object. Also converts values to their respective internal types.
     * @function fromObject
     * @memberof RepeatedInt32
     * @static
     * @param {Object.<string,*>} object Plain object
     * @returns {RepeatedInt32} RepeatedInt32
     */
    RepeatedInt32.fromObject = function (object, _depth) {
        if (object instanceof $root.RepeatedInt32)
            return object;
        if (!$util.isObject(object))
            throw $TypeError(".RepeatedInt32: object expected");
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $util.recursionLimit)
            throw $Error("max depth exceeded");
        let message = new $root.RepeatedInt32();
        if (object.numbers) {
            if (!$Array.isArray(object.numbers))
                throw $TypeError(".RepeatedInt32.numbers: array expected");
            message.numbers = $Array(object.numbers.length);
            for (let i = 0; i < object.numbers.length; ++i)
                message.numbers[i] = object.numbers[i] | 0;
        }
        return message;
    };

    /**
     * Creates a plain object from a RepeatedInt32 message. Also converts values to other types if specified.
     * @function toObject
     * @memberof RepeatedInt32
     * @static
     * @param {RepeatedInt32} message RepeatedInt32
     * @param {$protobuf.IConversionOptions} [options] Conversion options
     * @returns {Object.<string,*>} Plain object
     */
    RepeatedInt32.toObject = function (message, options, _depth) {
        if (!options)
            options = {};
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $util.recursionLimit)
            throw $Error("max depth exceeded");
        let object = {};
        if (options.arrays || options.defaults)
            object.numbers = [];
        if (message.numbers && message.numbers.length) {
            object.numbers = $Array(message.numbers.length);
            for (let j = 0; j < message.numbers.length; ++j)
                object.numbers[j] = message.numbers[j];
        }
        return object;
    };

    /**
     * Converts this RepeatedInt32 to JSON.
     * @function toJSON
     * @memberof RepeatedInt32
     * @instance
     * @returns {Object.<string,*>} JSON object
     */
    RepeatedInt32.prototype.toJSON = function() {
        return RepeatedInt32.toObject(this, $protobuf.util.toJSONOptions);
    };

    /**
     * Gets the type url for RepeatedInt32
     * @function getTypeUrl
     * @memberof RepeatedInt32
     * @static
     * @param {string} [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
     * @returns {string} The type url
     */
    RepeatedInt32.getTypeUrl = function(prefix) {
        if (prefix === $undefined)
            prefix = "type.googleapis.com";
        return prefix + "/RepeatedInt32";
    };

    return RepeatedInt32;
})();

export const RepeatedInt64 = $root.RepeatedInt64 = (() => {

    /**
     * Properties of a RepeatedInt64.
     * @typedef {Object} RepeatedInt64.$Properties
     * @property {Array.<number|Long>|null} [numbers] RepeatedInt64 numbers
     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
     */

    /**
     * Properties of a RepeatedInt64.
     * @name IRepeatedInt64
     * @interface IRepeatedInt64
     * @augments RepeatedInt64.$Properties
     * @deprecated Use RepeatedInt64.$Properties instead.
     */

    /**
     * Shape of a RepeatedInt64.
     * @typedef {RepeatedInt64.$Properties} RepeatedInt64.$Shape
     */

    /**
     * Constructs a new RepeatedInt64.
     * @name RepeatedInt64
     * @classdesc Represents a RepeatedInt64.
     * @constructor
     * @param {RepeatedInt64.$Properties=} [properties] Properties to set
     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
     */
    const RepeatedInt64 = function (properties) {
        this.numbers = [];
        if (properties)
            for (let keys = $Object.keys(properties), i = 0; i < keys.length; ++i)
                if (properties[keys[i]] != null && keys[i] !== "__proto__")
                    this[keys[i]] = properties[keys[i]];
    };

    /**
     * RepeatedInt64 numbers.
     * @member {Array.<number|Long>} numbers
     * @memberof RepeatedInt64
     * @instance
     */
    RepeatedInt64.prototype.numbers = $util.emptyArray;

    /**
     * Creates a new RepeatedInt64 instance using the specified properties.
     * @function create
     * @memberof RepeatedInt64
     * @static
     * @param {RepeatedInt64.$Properties=} [properties] Properties to set
     * @returns {RepeatedInt64} RepeatedInt64 instance
     * @type {{
     *   (properties: RepeatedInt64.$Shape): RepeatedInt64 & RepeatedInt64.$Shape;
     *   (properties?: RepeatedInt64.$Properties): RepeatedInt64;
     * }}
     */
    RepeatedInt64.create = function(properties) {
        return new RepeatedInt64(properties);
    };

    /**
     * Encodes the specified RepeatedInt64 message. Does not implicitly {@link RepeatedInt64.verify|verify} messages.
     * @function encode
     * @memberof RepeatedInt64
     * @static
     * @param {RepeatedInt64.$Properties} message RepeatedInt64 message or plain object to encode
     * @param {$protobuf.Writer} [writer] Writer to encode to
     * @returns {$protobuf.Writer} Writer
     */
    RepeatedInt64.encode = function (message, writer, _depth) {
        if (!writer)
            writer = $Writer.create();
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $util.recursionLimit)
            throw $Error("max depth exceeded");
        if (message.numbers != null && message.numbers.length) {
            writer.uint32(/* id 1, wireType 2 =*/10).fork();
            for (let i = 0; i < message.numbers.length; ++i)
                writer.int64(message.numbers[i]);
            writer.ldelim();
        }
        if (message.$unknowns != null && $Object.hasOwnProperty.call(message, "$unknowns"))
            for (let i = 0; i < message.$unknowns.length; ++i)
                writer.raw(message.$unknowns[i]);
        return writer;
    };

    /**
     * Encodes the specified RepeatedInt64 message, length delimited. Does not implicitly {@link RepeatedInt64.verify|verify} messages.
     * @function encodeDelimited
     * @memberof RepeatedInt64
     * @static
     * @param {RepeatedInt64.$Properties} message RepeatedInt64 message or plain object to encode
     * @param {$protobuf.Writer} [writer] Writer to encode to
     * @returns {$protobuf.Writer} Writer
     */
    RepeatedInt64.encodeDelimited = function(message, writer) {
        return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
    };

    /**
     * Decodes a RepeatedInt64 message from the specified reader or buffer.
     * @function decode
     * @memberof RepeatedInt64
     * @static
     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
     * @param {number} [length] Message length if known beforehand
     * @returns {RepeatedInt64 & RepeatedInt64.$Shape} RepeatedInt64
     * @throws {Error} If the payload is not a reader or valid buffer
     * @throws {$protobuf.util.ProtocolError} If required fields are missing
     */
    RepeatedInt64.decode = function (reader, length, _end, _depth, _target) {
        if (!(reader instanceof $Reader))
            reader = $Reader.create(reader);
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $Reader.recursionLimit)
            throw $Error("max depth exceeded");
        let end = length === $undefined ? reader.len : reader.pos + length, message = _target || new $root.RepeatedInt64();
        while (reader.pos < end) {
            let start = reader.pos;
            let tag = reader.tag();
            if (tag === _end) {
                _end = $undefined;
                break;
            }
            let wireType = tag & 7;
            switch (tag >>>= 3) {
            case 1: {
                    if (wireType === 2) {
                        if (!(message.numbers && message.numbers.length))
                            message.numbers = [];
                        let end2 = reader.uint32() + reader.pos;
                        while (reader.pos < end2)
                            message.numbers.push(reader.int64());
                        continue;
                    }
                    if (wireType !== 0)
                        break;
                    if (!(message.numbers && message.numbers.length))
                        message.numbers = [];
                    message.numbers.push(reader.int64());
                    continue;
                }
            }
            reader.skipType(wireType, _depth, tag);
            if (!reader.discardUnknown) {
                $util.makeProp(message, "$unknowns", false);
                (message.$unknowns || (message.$unknowns = [])).push(reader.raw(start, reader.pos));
            }
        }
        if (_end !== $undefined)
            throw $Error("missing end group");
        return message;
    };

    /**
     * Decodes a RepeatedInt64 message from the specified reader or buffer, length delimited.
     * @function decodeDelimited
     * @memberof RepeatedInt64
     * @static
     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
     * @returns {RepeatedInt64 & RepeatedInt64.$Shape} RepeatedInt64
     * @throws {Error} If the payload is not a reader or valid buffer
     * @throws {$protobuf.util.ProtocolError} If required fields are missing
     */
    RepeatedInt64.decodeDelimited = function(reader) {
        if (!(reader instanceof $Reader))
            reader = new $Reader(reader);
        return this.decode(reader, reader.uint32());
    };

    /**
     * Verifies a RepeatedInt64 message.
     * @function verify
     * @memberof RepeatedInt64
     * @static
     * @param {Object.<string,*>} message Plain object to verify
     * @returns {string|null} `null` if valid, otherwise the reason why it is not
     */
    RepeatedInt64.verify = function (message, _depth) {
        if (typeof message !== "object" || message === null)
            return "object expected";
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $util.recursionLimit)
            return "max depth exceeded";
        if (message.numbers != null && $Object.hasOwnProperty.call(message, "numbers")) {
            if (!$Array.isArray(message.numbers))
                return "numbers: array expected";
            for (let i = 0; i < message.numbers.length; ++i)
                if (!$util.isInteger(message.numbers[i]) && !(message.numbers[i] && $util.isInteger(message.numbers[i].low) && $util.isInteger(message.numbers[i].high)))
                    return "numbers: integer|Long[] expected";
        }
        return null;
    };

    /**
     * Creates a RepeatedInt64 message from a plain object. Also converts values to their respective internal types.
     * @function fromObject
     * @memberof RepeatedInt64
     * @static
     * @param {Object.<string,*>} object Plain object
     * @returns {RepeatedInt64} RepeatedInt64
     */
    RepeatedInt64.fromObject = function (object, _depth) {
        if (object instanceof $root.RepeatedInt64)
            return object;
        if (!$util.isObject(object))
            throw $TypeError(".RepeatedInt64: object expected");
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $util.recursionLimit)
            throw $Error("max depth exceeded");
        let message = new $root.RepeatedInt64();
        if (object.numbers) {
            if (!$Array.isArray(object.numbers))
                throw $TypeError(".RepeatedInt64.numbers: array expected");
            message.numbers = $Array(object.numbers.length);
            for (let i = 0; i < object.numbers.length; ++i)
                if ($util.Long)
                    message.numbers[i] = $util.Long.fromValue(object.numbers[i], false);
                else if (typeof object.numbers[i] === "string")
                    message.numbers[i] = $parseInt(object.numbers[i], 10);
                else if (typeof object.numbers[i] === "number")
                    message.numbers[i] = object.numbers[i];
                else if (typeof object.numbers[i] === "object")
                    message.numbers[i] = new $util.LongBits(object.numbers[i].low >>> 0, object.numbers[i].high >>> 0).toNumber();
        }
        return message;
    };

    /**
     * Creates a plain object from a RepeatedInt64 message. Also converts values to other types if specified.
     * @function toObject
     * @memberof RepeatedInt64
     * @static
     * @param {RepeatedInt64} message RepeatedInt64
     * @param {$protobuf.IConversionOptions} [options] Conversion options
     * @returns {Object.<string,*>} Plain object
     */
    RepeatedInt64.toObject = function (message, options, _depth) {
        if (!options)
            options = {};
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $util.recursionLimit)
            throw $Error("max depth exceeded");
        let object = {};
        if (options.arrays || options.defaults)
            object.numbers = [];
        if (message.numbers && message.numbers.length) {
            object.numbers = $Array(message.numbers.length);
            for (let j = 0; j < message.numbers.length; ++j)
                if (typeof $BigInt !== "undefined" && options.longs === $BigInt)
                    object.numbers[j] = typeof message.numbers[j] === "number" ? $BigInt(message.numbers[j]) : $util.Long.fromBits(message.numbers[j].low >>> 0, message.numbers[j].high >>> 0, false).toBigInt();
                else if (typeof message.numbers[j] === "number")
                    object.numbers[j] = options.longs === $String ? $String(message.numbers[j]) : message.numbers[j];
                else
                    object.numbers[j] = options.longs === $String ? $util.Long.prototype.toString.call(message.numbers[j]) : options.longs === $Number ? new $util.LongBits(message.numbers[j].low >>> 0, message.numbers[j].high >>> 0).toNumber() : message.numbers[j];
        }
        return object;
    };

    /**
     * Converts this RepeatedInt64 to JSON.
     * @function toJSON
     * @memberof RepeatedInt64
     * @instance
     * @returns {Object.<string,*>} JSON object
     */
    RepeatedInt64.prototype.toJSON = function() {
        return RepeatedInt64.toObject(this, $protobuf.util.toJSONOptions);
    };

    /**
     * Gets the type url for RepeatedInt64
     * @function getTypeUrl
     * @memberof RepeatedInt64
     * @static
     * @param {string} [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
     * @returns {string} The type url
     */
    RepeatedInt64.getTypeUrl = function(prefix) {
        if (prefix === $undefined)
            prefix = "type.googleapis.com";
        return prefix + "/RepeatedInt64";
    };

    return RepeatedInt64;
})();

export const RepeatedFloat = $root.RepeatedFloat = (() => {

    /**
     * Properties of a RepeatedFloat.
     * @typedef {Object} RepeatedFloat.$Properties
     * @property {Array.<number>|null} [numbers] RepeatedFloat numbers
     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
     */

    /**
     * Properties of a RepeatedFloat.
     * @name IRepeatedFloat
     * @interface IRepeatedFloat
     * @augments RepeatedFloat.$Properties
     * @deprecated Use RepeatedFloat.$Properties instead.
     */

    /**
     * Shape of a RepeatedFloat.
     * @typedef {RepeatedFloat.$Properties} RepeatedFloat.$Shape
     */

    /**
     * Constructs a new RepeatedFloat.
     * @name RepeatedFloat
     * @classdesc Represents a RepeatedFloat.
     * @constructor
     * @param {RepeatedFloat.$Properties=} [properties] Properties to set
     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
     */
    const RepeatedFloat = function (properties) {
        this.numbers = [];
        if (properties)
            for (let keys = $Object.keys(properties), i = 0; i < keys.length; ++i)
                if (properties[keys[i]] != null && keys[i] !== "__proto__")
                    this[keys[i]] = properties[keys[i]];
    };

    /**
     * RepeatedFloat numbers.
     * @member {Array.<number>} numbers
     * @memberof RepeatedFloat
     * @instance
     */
    RepeatedFloat.prototype.numbers = $util.emptyArray;

    /**
     * Creates a new RepeatedFloat instance using the specified properties.
     * @function create
     * @memberof RepeatedFloat
     * @static
     * @param {RepeatedFloat.$Properties=} [properties] Properties to set
     * @returns {RepeatedFloat} RepeatedFloat instance
     * @type {{
     *   (properties: RepeatedFloat.$Shape): RepeatedFloat & RepeatedFloat.$Shape;
     *   (properties?: RepeatedFloat.$Properties): RepeatedFloat;
     * }}
     */
    RepeatedFloat.create = function(properties) {
        return new RepeatedFloat(properties);
    };

    /**
     * Encodes the specified RepeatedFloat message. Does not implicitly {@link RepeatedFloat.verify|verify} messages.
     * @function encode
     * @memberof RepeatedFloat
     * @static
     * @param {RepeatedFloat.$Properties} message RepeatedFloat message or plain object to encode
     * @param {$protobuf.Writer} [writer] Writer to encode to
     * @returns {$protobuf.Writer} Writer
     */
    RepeatedFloat.encode = function (message, writer, _depth) {
        if (!writer)
            writer = $Writer.create();
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $util.recursionLimit)
            throw $Error("max depth exceeded");
        if (message.numbers != null && message.numbers.length) {
            writer.uint32(/* id 1, wireType 2 =*/10).fork();
            for (let i = 0; i < message.numbers.length; ++i)
                writer.float(message.numbers[i]);
            writer.ldelim();
        }
        if (message.$unknowns != null && $Object.hasOwnProperty.call(message, "$unknowns"))
            for (let i = 0; i < message.$unknowns.length; ++i)
                writer.raw(message.$unknowns[i]);
        return writer;
    };

    /**
     * Encodes the specified RepeatedFloat message, length delimited. Does not implicitly {@link RepeatedFloat.verify|verify} messages.
     * @function encodeDelimited
     * @memberof RepeatedFloat
     * @static
     * @param {RepeatedFloat.$Properties} message RepeatedFloat message or plain object to encode
     * @param {$protobuf.Writer} [writer] Writer to encode to
     * @returns {$protobuf.Writer} Writer
     */
    RepeatedFloat.encodeDelimited = function(message, writer) {
        return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
    };

    /**
     * Decodes a RepeatedFloat message from the specified reader or buffer.
     * @function decode
     * @memberof RepeatedFloat
     * @static
     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
     * @param {number} [length] Message length if known beforehand
     * @returns {RepeatedFloat & RepeatedFloat.$Shape} RepeatedFloat
     * @throws {Error} If the payload is not a reader or valid buffer
     * @throws {$protobuf.util.ProtocolError} If required fields are missing
     */
    RepeatedFloat.decode = function (reader, length, _end, _depth, _target) {
        if (!(reader instanceof $Reader))
            reader = $Reader.create(reader);
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $Reader.recursionLimit)
            throw $Error("max depth exceeded");
        let end = length === $undefined ? reader.len : reader.pos + length, message = _target || new $root.RepeatedFloat();
        while (reader.pos < end) {
            let start = reader.pos;
            let tag = reader.tag();
            if (tag === _end) {
                _end = $undefined;
                break;
            }
            let wireType = tag & 7;
            switch (tag >>>= 3) {
            case 1: {
                    if (wireType === 2) {
                        if (!(message.numbers && message.numbers.length))
                            message.numbers = [];
                        let end2 = reader.uint32() + reader.pos;
                        while (reader.pos < end2)
                            message.numbers.push(reader.float());
                        continue;
                    }
                    if (wireType !== 5)
                        break;
                    if (!(message.numbers && message.numbers.length))
                        message.numbers = [];
                    message.numbers.push(reader.float());
                    continue;
                }
            }
            reader.skipType(wireType, _depth, tag);
            if (!reader.discardUnknown) {
                $util.makeProp(message, "$unknowns", false);
                (message.$unknowns || (message.$unknowns = [])).push(reader.raw(start, reader.pos));
            }
        }
        if (_end !== $undefined)
            throw $Error("missing end group");
        return message;
    };

    /**
     * Decodes a RepeatedFloat message from the specified reader or buffer, length delimited.
     * @function decodeDelimited
     * @memberof RepeatedFloat
     * @static
     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
     * @returns {RepeatedFloat & RepeatedFloat.$Shape} RepeatedFloat
     * @throws {Error} If the payload is not a reader or valid buffer
     * @throws {$protobuf.util.ProtocolError} If required fields are missing
     */
    RepeatedFloat.decodeDelimited = function(reader) {
        if (!(reader instanceof $Reader))
            reader = new $Reader(reader);
        return this.decode(reader, reader.uint32());
    };

    /**
     * Verifies a RepeatedFloat message.
     * @function verify
     * @memberof RepeatedFloat
     * @static
     * @param {Object.<string,*>} message Plain object to verify
     * @returns {string|null} `null` if valid, otherwise the reason why it is not
     */
    RepeatedFloat.verify = function (message, _depth) {
        if (typeof message !== "object" || message === null)
            return "object expected";
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $util.recursionLimit)
            return "max depth exceeded";
        if (message.numbers != null && $Object.hasOwnProperty.call(message, "numbers")) {
            if (!$Array.isArray(message.numbers))
                return "numbers: array expected";
            for (let i = 0; i < message.numbers.length; ++i)
                if (typeof message.numbers[i] !== "number")
                    return "numbers: number[] expected";
        }
        return null;
    };

    /**
     * Creates a RepeatedFloat message from a plain object. Also converts values to their respective internal types.
     * @function fromObject
     * @memberof RepeatedFloat
     * @static
     * @param {Object.<string,*>} object Plain object
     * @returns {RepeatedFloat} RepeatedFloat
     */
    RepeatedFloat.fromObject = function (object, _depth) {
        if (object instanceof $root.RepeatedFloat)
            return object;
        if (!$util.isObject(object))
            throw $TypeError(".RepeatedFloat: object expected");
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $util.recursionLimit)
            throw $Error("max depth exceeded");
        let message = new $root.RepeatedFloat();
        if (object.numbers) {
            if (!$Array.isArray(object.numbers))
                throw $TypeError(".RepeatedFloat.numbers: array expected");
            message.numbers = $Array(object.numbers.length);
            for (let i = 0; i < object.numbers.length; ++i)
                message.numbers[i] = $Number(object.numbers[i]);
        }
        return message;
    };

    /**
     * Creates a plain object from a RepeatedFloat message. Also converts values to other types if specified.
     * @function toObject
     * @memberof RepeatedFloat
     * @static
     * @param {RepeatedFloat} message RepeatedFloat
     * @param {$protobuf.IConversionOptions} [options] Conversion options
     * @returns {Object.<string,*>} Plain object
     */
    RepeatedFloat.toObject = function (message, options, _depth) {
        if (!options)
            options = {};
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $util.recursionLimit)
            throw $Error("max depth exceeded");
        let object = {};
        if (options.arrays || options.defaults)
            object.numbers = [];
        if (message.numbers && message.numbers.length) {
            object.numbers = $Array(message.numbers.length);
            for (let j = 0; j < message.numbers.length; ++j)
                object.numbers[j] = options.json && !$isFinite(message.numbers[j]) ? $String(message.numbers[j]) : message.numbers[j];
        }
        return object;
    };

    /**
     * Converts this RepeatedFloat to JSON.
     * @function toJSON
     * @memberof RepeatedFloat
     * @instance
     * @returns {Object.<string,*>} JSON object
     */
    RepeatedFloat.prototype.toJSON = function() {
        return RepeatedFloat.toObject(this, $protobuf.util.toJSONOptions);
    };

    /**
     * Gets the type url for RepeatedFloat
     * @function getTypeUrl
     * @memberof RepeatedFloat
     * @static
     * @param {string} [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
     * @returns {string} The type url
     */
    RepeatedFloat.getTypeUrl = function(prefix) {
        if (prefix === $undefined)
            prefix = "type.googleapis.com";
        return prefix + "/RepeatedFloat";
    };

    return RepeatedFloat;
})();

export const RepeatedDouble = $root.RepeatedDouble = (() => {

    /**
     * Properties of a RepeatedDouble.
     * @typedef {Object} RepeatedDouble.$Properties
     * @property {Array.<number>|null} [numbers] RepeatedDouble numbers
     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
     */

    /**
     * Properties of a RepeatedDouble.
     * @name IRepeatedDouble
     * @interface IRepeatedDouble
     * @augments RepeatedDouble.$Properties
     * @deprecated Use RepeatedDouble.$Properties instead.
     */

    /**
     * Shape of a RepeatedDouble.
     * @typedef {RepeatedDouble.$Properties} RepeatedDouble.$Shape
     */

    /**
     * Constructs a new RepeatedDouble.
     * @name RepeatedDouble
     * @classdesc Represents a RepeatedDouble.
     * @constructor
     * @param {RepeatedDouble.$Properties=} [properties] Properties to set
     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
     */
    const RepeatedDouble = function (properties) {
        this.numbers = [];
        if (properties)
            for (let keys = $Object.keys(properties), i = 0; i < keys.length; ++i)
                if (properties[keys[i]] != null && keys[i] !== "__proto__")
                    this[keys[i]] = properties[keys[i]];
    };

    /**
     * RepeatedDouble numbers.
     * @member {Array.<number>} numbers
     * @memberof RepeatedDouble
     * @instance
     */
    RepeatedDouble.prototype.numbers = $util.emptyArray;

    /**
     * Creates a new RepeatedDouble instance using the specified properties.
     * @function create
     * @memberof RepeatedDouble
     * @static
     * @param {RepeatedDouble.$Properties=} [properties] Properties to set
     * @returns {RepeatedDouble} RepeatedDouble instance
     * @type {{
     *   (properties: RepeatedDouble.$Shape): RepeatedDouble & RepeatedDouble.$Shape;
     *   (properties?: RepeatedDouble.$Properties): RepeatedDouble;
     * }}
     */
    RepeatedDouble.create = function(properties) {
        return new RepeatedDouble(properties);
    };

    /**
     * Encodes the specified RepeatedDouble message. Does not implicitly {@link RepeatedDouble.verify|verify} messages.
     * @function encode
     * @memberof RepeatedDouble
     * @static
     * @param {RepeatedDouble.$Properties} message RepeatedDouble message or plain object to encode
     * @param {$protobuf.Writer} [writer] Writer to encode to
     * @returns {$protobuf.Writer} Writer
     */
    RepeatedDouble.encode = function (message, writer, _depth) {
        if (!writer)
            writer = $Writer.create();
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $util.recursionLimit)
            throw $Error("max depth exceeded");
        if (message.numbers != null && message.numbers.length) {
            writer.uint32(/* id 1, wireType 2 =*/10).fork();
            for (let i = 0; i < message.numbers.length; ++i)
                writer.double(message.numbers[i]);
            writer.ldelim();
        }
        if (message.$unknowns != null && $Object.hasOwnProperty.call(message, "$unknowns"))
            for (let i = 0; i < message.$unknowns.length; ++i)
                writer.raw(message.$unknowns[i]);
        return writer;
    };

    /**
     * Encodes the specified RepeatedDouble message, length delimited. Does not implicitly {@link RepeatedDouble.verify|verify} messages.
     * @function encodeDelimited
     * @memberof RepeatedDouble
     * @static
     * @param {RepeatedDouble.$Properties} message RepeatedDouble message or plain object to encode
     * @param {$protobuf.Writer} [writer] Writer to encode to
     * @returns {$protobuf.Writer} Writer
     */
    RepeatedDouble.encodeDelimited = function(message, writer) {
        return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
    };

    /**
     * Decodes a RepeatedDouble message from the specified reader or buffer.
     * @function decode
     * @memberof RepeatedDouble
     * @static
     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
     * @param {number} [length] Message length if known beforehand
     * @returns {RepeatedDouble & RepeatedDouble.$Shape} RepeatedDouble
     * @throws {Error} If the payload is not a reader or valid buffer
     * @throws {$protobuf.util.ProtocolError} If required fields are missing
     */
    RepeatedDouble.decode = function (reader, length, _end, _depth, _target) {
        if (!(reader instanceof $Reader))
            reader = $Reader.create(reader);
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $Reader.recursionLimit)
            throw $Error("max depth exceeded");
        let end = length === $undefined ? reader.len : reader.pos + length, message = _target || new $root.RepeatedDouble();
        while (reader.pos < end) {
            let start = reader.pos;
            let tag = reader.tag();
            if (tag === _end) {
                _end = $undefined;
                break;
            }
            let wireType = tag & 7;
            switch (tag >>>= 3) {
            case 1: {
                    if (wireType === 2) {
                        if (!(message.numbers && message.numbers.length))
                            message.numbers = [];
                        let end2 = reader.uint32() + reader.pos;
                        while (reader.pos < end2)
                            message.numbers.push(reader.double());
                        continue;
                    }
                    if (wireType !== 1)
                        break;
                    if (!(message.numbers && message.numbers.length))
                        message.numbers = [];
                    message.numbers.push(reader.double());
                    continue;
                }
            }
            reader.skipType(wireType, _depth, tag);
            if (!reader.discardUnknown) {
                $util.makeProp(message, "$unknowns", false);
                (message.$unknowns || (message.$unknowns = [])).push(reader.raw(start, reader.pos));
            }
        }
        if (_end !== $undefined)
            throw $Error("missing end group");
        return message;
    };

    /**
     * Decodes a RepeatedDouble message from the specified reader or buffer, length delimited.
     * @function decodeDelimited
     * @memberof RepeatedDouble
     * @static
     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
     * @returns {RepeatedDouble & RepeatedDouble.$Shape} RepeatedDouble
     * @throws {Error} If the payload is not a reader or valid buffer
     * @throws {$protobuf.util.ProtocolError} If required fields are missing
     */
    RepeatedDouble.decodeDelimited = function(reader) {
        if (!(reader instanceof $Reader))
            reader = new $Reader(reader);
        return this.decode(reader, reader.uint32());
    };

    /**
     * Verifies a RepeatedDouble message.
     * @function verify
     * @memberof RepeatedDouble
     * @static
     * @param {Object.<string,*>} message Plain object to verify
     * @returns {string|null} `null` if valid, otherwise the reason why it is not
     */
    RepeatedDouble.verify = function (message, _depth) {
        if (typeof message !== "object" || message === null)
            return "object expected";
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $util.recursionLimit)
            return "max depth exceeded";
        if (message.numbers != null && $Object.hasOwnProperty.call(message, "numbers")) {
            if (!$Array.isArray(message.numbers))
                return "numbers: array expected";
            for (let i = 0; i < message.numbers.length; ++i)
                if (typeof message.numbers[i] !== "number")
                    return "numbers: number[] expected";
        }
        return null;
    };

    /**
     * Creates a RepeatedDouble message from a plain object. Also converts values to their respective internal types.
     * @function fromObject
     * @memberof RepeatedDouble
     * @static
     * @param {Object.<string,*>} object Plain object
     * @returns {RepeatedDouble} RepeatedDouble
     */
    RepeatedDouble.fromObject = function (object, _depth) {
        if (object instanceof $root.RepeatedDouble)
            return object;
        if (!$util.isObject(object))
            throw $TypeError(".RepeatedDouble: object expected");
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $util.recursionLimit)
            throw $Error("max depth exceeded");
        let message = new $root.RepeatedDouble();
        if (object.numbers) {
            if (!$Array.isArray(object.numbers))
                throw $TypeError(".RepeatedDouble.numbers: array expected");
            message.numbers = $Array(object.numbers.length);
            for (let i = 0; i < object.numbers.length; ++i)
                message.numbers[i] = $Number(object.numbers[i]);
        }
        return message;
    };

    /**
     * Creates a plain object from a RepeatedDouble message. Also converts values to other types if specified.
     * @function toObject
     * @memberof RepeatedDouble
     * @static
     * @param {RepeatedDouble} message RepeatedDouble
     * @param {$protobuf.IConversionOptions} [options] Conversion options
     * @returns {Object.<string,*>} Plain object
     */
    RepeatedDouble.toObject = function (message, options, _depth) {
        if (!options)
            options = {};
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $util.recursionLimit)
            throw $Error("max depth exceeded");
        let object = {};
        if (options.arrays || options.defaults)
            object.numbers = [];
        if (message.numbers && message.numbers.length) {
            object.numbers = $Array(message.numbers.length);
            for (let j = 0; j < message.numbers.length; ++j)
                object.numbers[j] = options.json && !$isFinite(message.numbers[j]) ? $String(message.numbers[j]) : message.numbers[j];
        }
        return object;
    };

    /**
     * Converts this RepeatedDouble to JSON.
     * @function toJSON
     * @memberof RepeatedDouble
     * @instance
     * @returns {Object.<string,*>} JSON object
     */
    RepeatedDouble.prototype.toJSON = function() {
        return RepeatedDouble.toObject(this, $protobuf.util.toJSONOptions);
    };

    /**
     * Gets the type url for RepeatedDouble
     * @function getTypeUrl
     * @memberof RepeatedDouble
     * @static
     * @param {string} [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
     * @returns {string} The type url
     */
    RepeatedDouble.getTypeUrl = function(prefix) {
        if (prefix === $undefined)
            prefix = "type.googleapis.com";
        return prefix + "/RepeatedDouble";
    };

    return RepeatedDouble;
})();

export const ElementMessage = $root.ElementMessage = (() => {

    /**
     * Properties of an ElementMessage.
     * @typedef {Object} ElementMessage.$Properties
     * @property {string|null} [type] ElementMessage type
     * @property {Uint8Array|null} [id] ElementMessage id
     * @property {string|null} [objectId] ElementMessage objectId
     * @property {Uint8Array|null} [source] ElementMessage source
     * @property {string|null} [displayName] ElementMessage displayName
     * @property {Array.<number>|null} [positions] ElementMessage positions
     * @property {number|Long|null} [stale] ElementMessage stale
     * @property {number|Long|null} [start] ElementMessage start
     * @property {number|Long|null} [time] ElementMessage time
     * @property {Object.<string,Value.$Properties>|null} [values] ElementMessage values
     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
     */

    /**
     * Properties of an ElementMessage.
     * @name IElementMessage
     * @interface IElementMessage
     * @augments ElementMessage.$Properties
     * @deprecated Use ElementMessage.$Properties instead.
     */

    /**
     * Shape of an ElementMessage.
     * @typedef {{
     *   type?: string|null;
     *   id?: Uint8Array|null;
     *   objectId?: string|null;
     *   source?: Uint8Array|null;
     *   displayName?: string|null;
     *   positions?: Array.<number>|null;
     *   stale?: number|Long|null;
     *   start?: number|Long|null;
     *   time?: number|Long|null;
     *   values?: Object.<string,Value.$Shape>|null;
     *   $unknowns?: Array.<Uint8Array>;
     * }} ElementMessage.$Shape
     */

    /**
     * Constructs a new ElementMessage.
     * @name ElementMessage
     * @classdesc Represents an ElementMessage.
     * @constructor
     * @param {ElementMessage.$Properties=} [properties] Properties to set
     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
     */
    const ElementMessage = function (properties) {
        this.positions = [];
        this.values = {};
        if (properties)
            for (let keys = $Object.keys(properties), i = 0; i < keys.length; ++i)
                if (properties[keys[i]] != null && keys[i] !== "__proto__")
                    this[keys[i]] = properties[keys[i]];
    };

    /**
     * ElementMessage type.
     * @member {string} type
     * @memberof ElementMessage
     * @instance
     */
    ElementMessage.prototype.type = "";

    /**
     * ElementMessage id.
     * @member {Uint8Array} id
     * @memberof ElementMessage
     * @instance
     */
    ElementMessage.prototype.id = $util.newBuffer([]);

    /**
     * ElementMessage objectId.
     * @member {string} objectId
     * @memberof ElementMessage
     * @instance
     */
    ElementMessage.prototype.objectId = "";

    /**
     * ElementMessage source.
     * @member {Uint8Array} source
     * @memberof ElementMessage
     * @instance
     */
    ElementMessage.prototype.source = $util.newBuffer([]);

    /**
     * ElementMessage displayName.
     * @member {string} displayName
     * @memberof ElementMessage
     * @instance
     */
    ElementMessage.prototype.displayName = "";

    /**
     * ElementMessage positions.
     * @member {Array.<number>} positions
     * @memberof ElementMessage
     * @instance
     */
    ElementMessage.prototype.positions = $util.emptyArray;

    /**
     * ElementMessage stale.
     * @member {number|Long} stale
     * @memberof ElementMessage
     * @instance
     */
    ElementMessage.prototype.stale = $util.Long ? $util.Long.fromBits(0,0,true) : 0;

    /**
     * ElementMessage start.
     * @member {number|Long} start
     * @memberof ElementMessage
     * @instance
     */
    ElementMessage.prototype.start = $util.Long ? $util.Long.fromBits(0,0,true) : 0;

    /**
     * ElementMessage time.
     * @member {number|Long} time
     * @memberof ElementMessage
     * @instance
     */
    ElementMessage.prototype.time = $util.Long ? $util.Long.fromBits(0,0,true) : 0;

    /**
     * ElementMessage values.
     * @member {Object.<string,Value.$Properties>} values
     * @memberof ElementMessage
     * @instance
     */
    ElementMessage.prototype.values = $util.emptyObject;

    /**
     * Creates a new ElementMessage instance using the specified properties.
     * @function create
     * @memberof ElementMessage
     * @static
     * @param {ElementMessage.$Properties=} [properties] Properties to set
     * @returns {ElementMessage} ElementMessage instance
     * @type {{
     *   (properties: ElementMessage.$Shape): ElementMessage & ElementMessage.$Shape;
     *   (properties?: ElementMessage.$Properties): ElementMessage;
     * }}
     */
    ElementMessage.create = function(properties) {
        return new ElementMessage(properties);
    };

    /**
     * Encodes the specified ElementMessage message. Does not implicitly {@link ElementMessage.verify|verify} messages.
     * @function encode
     * @memberof ElementMessage
     * @static
     * @param {ElementMessage.$Properties} message ElementMessage message or plain object to encode
     * @param {$protobuf.Writer} [writer] Writer to encode to
     * @returns {$protobuf.Writer} Writer
     */
    ElementMessage.encode = function (message, writer, _depth) {
        if (!writer)
            writer = $Writer.create();
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $util.recursionLimit)
            throw $Error("max depth exceeded");
        if (message.type != null && $Object.hasOwnProperty.call(message, "type") && message.type !== "")
            writer.uint32(/* id 1, wireType 2 =*/10).string(message.type);
        if (message.id != null && $Object.hasOwnProperty.call(message, "id") && message.id.length)
            writer.uint32(/* id 2, wireType 2 =*/18).bytes(message.id);
        if (message.objectId != null && $Object.hasOwnProperty.call(message, "objectId") && message.objectId !== "")
            writer.uint32(/* id 3, wireType 2 =*/26).string(message.objectId);
        if (message.displayName != null && $Object.hasOwnProperty.call(message, "displayName") && message.displayName !== "")
            writer.uint32(/* id 4, wireType 2 =*/34).string(message.displayName);
        if (message.positions != null && message.positions.length) {
            writer.uint32(/* id 5, wireType 2 =*/42).fork();
            for (let i = 0; i < message.positions.length; ++i)
                writer.double(message.positions[i]);
            writer.ldelim();
        }
        if (message.stale != null && $Object.hasOwnProperty.call(message, "stale") && (typeof message.stale === "object" ? message.stale.low || message.stale.high : message.stale !== 0))
            writer.uint32(/* id 6, wireType 0 =*/48).uint64(message.stale);
        if (message.values != null && $Object.hasOwnProperty.call(message, "values"))
            for (let keys = $Object.keys(message.values), i = 0; i < keys.length; ++i) {
                writer.uint32(/* id 7, wireType 2 =*/58).fork().uint32(/* id 1, wireType 2 =*/10).string(keys[i]);
                $root.Value.encode(message.values[keys[i]], writer.uint32(/* id 2, wireType 2 =*/18).fork(), _depth + 1).ldelim().ldelim();
            }
        if (message.source != null && $Object.hasOwnProperty.call(message, "source") && message.source.length)
            writer.uint32(/* id 8, wireType 2 =*/66).bytes(message.source);
        if (message.start != null && $Object.hasOwnProperty.call(message, "start") && (typeof message.start === "object" ? message.start.low || message.start.high : message.start !== 0))
            writer.uint32(/* id 9, wireType 0 =*/72).uint64(message.start);
        if (message.time != null && $Object.hasOwnProperty.call(message, "time") && (typeof message.time === "object" ? message.time.low || message.time.high : message.time !== 0))
            writer.uint32(/* id 10, wireType 0 =*/80).uint64(message.time);
        if (message.$unknowns != null && $Object.hasOwnProperty.call(message, "$unknowns"))
            for (let i = 0; i < message.$unknowns.length; ++i)
                writer.raw(message.$unknowns[i]);
        return writer;
    };

    /**
     * Encodes the specified ElementMessage message, length delimited. Does not implicitly {@link ElementMessage.verify|verify} messages.
     * @function encodeDelimited
     * @memberof ElementMessage
     * @static
     * @param {ElementMessage.$Properties} message ElementMessage message or plain object to encode
     * @param {$protobuf.Writer} [writer] Writer to encode to
     * @returns {$protobuf.Writer} Writer
     */
    ElementMessage.encodeDelimited = function(message, writer) {
        return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
    };

    /**
     * Decodes an ElementMessage message from the specified reader or buffer.
     * @function decode
     * @memberof ElementMessage
     * @static
     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
     * @param {number} [length] Message length if known beforehand
     * @returns {ElementMessage & ElementMessage.$Shape} ElementMessage
     * @throws {Error} If the payload is not a reader or valid buffer
     * @throws {$protobuf.util.ProtocolError} If required fields are missing
     */
    ElementMessage.decode = function (reader, length, _end, _depth, _target) {
        if (!(reader instanceof $Reader))
            reader = $Reader.create(reader);
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $Reader.recursionLimit)
            throw $Error("max depth exceeded");
        let end = length === $undefined ? reader.len : reader.pos + length, message = _target || new $root.ElementMessage(), key, value;
        while (reader.pos < end) {
            let start = reader.pos;
            let tag = reader.tag();
            if (tag === _end) {
                _end = $undefined;
                break;
            }
            let wireType = tag & 7;
            switch (tag >>>= 3) {
            case 1: {
                    if (wireType !== 2)
                        break;
                    if ((value = reader.stringVerify()).length)
                        message.type = value;
                    else
                        delete message.type;
                    continue;
                }
            case 2: {
                    if (wireType !== 2)
                        break;
                    if ((value = reader.bytes()).length)
                        message.id = value;
                    else
                        delete message.id;
                    continue;
                }
            case 3: {
                    if (wireType !== 2)
                        break;
                    if ((value = reader.stringVerify()).length)
                        message.objectId = value;
                    else
                        delete message.objectId;
                    continue;
                }
            case 8: {
                    if (wireType !== 2)
                        break;
                    if ((value = reader.bytes()).length)
                        message.source = value;
                    else
                        delete message.source;
                    continue;
                }
            case 4: {
                    if (wireType !== 2)
                        break;
                    if ((value = reader.stringVerify()).length)
                        message.displayName = value;
                    else
                        delete message.displayName;
                    continue;
                }
            case 5: {
                    if (wireType === 2) {
                        if (!(message.positions && message.positions.length))
                            message.positions = [];
                        let end2 = reader.uint32() + reader.pos;
                        while (reader.pos < end2)
                            message.positions.push(reader.double());
                        continue;
                    }
                    if (wireType !== 1)
                        break;
                    if (!(message.positions && message.positions.length))
                        message.positions = [];
                    message.positions.push(reader.double());
                    continue;
                }
            case 6: {
                    if (wireType !== 0)
                        break;
                    if (typeof (value = reader.uint64()) === "object" ? value.low || value.high : value !== 0)
                        message.stale = value;
                    else
                        delete message.stale;
                    continue;
                }
            case 9: {
                    if (wireType !== 0)
                        break;
                    if (typeof (value = reader.uint64()) === "object" ? value.low || value.high : value !== 0)
                        message.start = value;
                    else
                        delete message.start;
                    continue;
                }
            case 10: {
                    if (wireType !== 0)
                        break;
                    if (typeof (value = reader.uint64()) === "object" ? value.low || value.high : value !== 0)
                        message.time = value;
                    else
                        delete message.time;
                    continue;
                }
            case 7: {
                    if (wireType !== 2)
                        break;
                    if (message.values === $util.emptyObject)
                        message.values = {};
                    let end2 = reader.uint32() + reader.pos;
                    key = "";
                    value = null;
                    while (reader.pos < end2) {
                        let tag2 = reader.tag();
                        wireType = tag2 & 7;
                        switch (tag2 >>>= 3) {
                        case 1:
                            if (wireType !== 2)
                                break;
                            key = reader.stringVerify();
                            continue;
                        case 2:
                            if (wireType !== 2)
                                break;
                            value = $root.Value.decode(reader, reader.uint32(), $undefined, _depth + 1);
                            continue;
                        }
                        reader.skipType(wireType, _depth, tag2);
                    }
                    if (key === "__proto__")
                        $util.makeProp(message.values, key);
                    message.values[key] = value || new $root.Value();
                    continue;
                }
            }
            reader.skipType(wireType, _depth, tag);
            if (!reader.discardUnknown) {
                $util.makeProp(message, "$unknowns", false);
                (message.$unknowns || (message.$unknowns = [])).push(reader.raw(start, reader.pos));
            }
        }
        if (_end !== $undefined)
            throw $Error("missing end group");
        return message;
    };

    /**
     * Decodes an ElementMessage message from the specified reader or buffer, length delimited.
     * @function decodeDelimited
     * @memberof ElementMessage
     * @static
     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
     * @returns {ElementMessage & ElementMessage.$Shape} ElementMessage
     * @throws {Error} If the payload is not a reader or valid buffer
     * @throws {$protobuf.util.ProtocolError} If required fields are missing
     */
    ElementMessage.decodeDelimited = function(reader) {
        if (!(reader instanceof $Reader))
            reader = new $Reader(reader);
        return this.decode(reader, reader.uint32());
    };

    /**
     * Verifies an ElementMessage message.
     * @function verify
     * @memberof ElementMessage
     * @static
     * @param {Object.<string,*>} message Plain object to verify
     * @returns {string|null} `null` if valid, otherwise the reason why it is not
     */
    ElementMessage.verify = function (message, _depth) {
        if (typeof message !== "object" || message === null)
            return "object expected";
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $util.recursionLimit)
            return "max depth exceeded";
        if (message.type != null && $Object.hasOwnProperty.call(message, "type"))
            if (!$util.isString(message.type))
                return "type: string expected";
        if (message.id != null && $Object.hasOwnProperty.call(message, "id"))
            if (!(message.id && typeof message.id.length === "number" || $util.isString(message.id)))
                return "id: buffer expected";
        if (message.objectId != null && $Object.hasOwnProperty.call(message, "objectId"))
            if (!$util.isString(message.objectId))
                return "objectId: string expected";
        if (message.source != null && $Object.hasOwnProperty.call(message, "source"))
            if (!(message.source && typeof message.source.length === "number" || $util.isString(message.source)))
                return "source: buffer expected";
        if (message.displayName != null && $Object.hasOwnProperty.call(message, "displayName"))
            if (!$util.isString(message.displayName))
                return "displayName: string expected";
        if (message.positions != null && $Object.hasOwnProperty.call(message, "positions")) {
            if (!$Array.isArray(message.positions))
                return "positions: array expected";
            for (let i = 0; i < message.positions.length; ++i)
                if (typeof message.positions[i] !== "number")
                    return "positions: number[] expected";
        }
        if (message.stale != null && $Object.hasOwnProperty.call(message, "stale"))
            if (!$util.isInteger(message.stale) && !(message.stale && $util.isInteger(message.stale.low) && $util.isInteger(message.stale.high)))
                return "stale: integer|Long expected";
        if (message.start != null && $Object.hasOwnProperty.call(message, "start"))
            if (!$util.isInteger(message.start) && !(message.start && $util.isInteger(message.start.low) && $util.isInteger(message.start.high)))
                return "start: integer|Long expected";
        if (message.time != null && $Object.hasOwnProperty.call(message, "time"))
            if (!$util.isInteger(message.time) && !(message.time && $util.isInteger(message.time.low) && $util.isInteger(message.time.high)))
                return "time: integer|Long expected";
        if (message.values != null && $Object.hasOwnProperty.call(message, "values")) {
            if (!$util.isObject(message.values))
                return "values: object expected";
            let key = $Object.keys(message.values);
            for (let i = 0; i < key.length; ++i) {
                let error = $root.Value.verify(message.values[key[i]], _depth + 1);
                if (error)
                    return "values." + error;
            }
        }
        return null;
    };

    /**
     * Creates an ElementMessage message from a plain object. Also converts values to their respective internal types.
     * @function fromObject
     * @memberof ElementMessage
     * @static
     * @param {Object.<string,*>} object Plain object
     * @returns {ElementMessage} ElementMessage
     */
    ElementMessage.fromObject = function (object, _depth) {
        if (object instanceof $root.ElementMessage)
            return object;
        if (!$util.isObject(object))
            throw $TypeError(".ElementMessage: object expected");
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $util.recursionLimit)
            throw $Error("max depth exceeded");
        let message = new $root.ElementMessage();
        if (object.type != null)
            if (typeof object.type !== "string" || object.type.length)
                message.type = $String(object.type);
        if (object.id != null)
            if (object.id.length)
                if (typeof object.id === "string")
                    $util.base64.decode(object.id, message.id = $util.newBuffer($util.base64.length(object.id)), 0);
                else if (object.id.length >= 0)
                    message.id = object.id;
        if (object.objectId != null)
            if (typeof object.objectId !== "string" || object.objectId.length)
                message.objectId = $String(object.objectId);
        if (object.source != null)
            if (object.source.length)
                if (typeof object.source === "string")
                    $util.base64.decode(object.source, message.source = $util.newBuffer($util.base64.length(object.source)), 0);
                else if (object.source.length >= 0)
                    message.source = object.source;
        if (object.displayName != null)
            if (typeof object.displayName !== "string" || object.displayName.length)
                message.displayName = $String(object.displayName);
        if (object.positions) {
            if (!$Array.isArray(object.positions))
                throw $TypeError(".ElementMessage.positions: array expected");
            message.positions = $Array(object.positions.length);
            for (let i = 0; i < object.positions.length; ++i)
                message.positions[i] = $Number(object.positions[i]);
        }
        if (object.stale != null)
            if (typeof object.stale === "object" ? object.stale.low || object.stale.high : $Number(object.stale) !== 0)
                if ($util.Long)
                    message.stale = $util.Long.fromValue(object.stale, true);
                else if (typeof object.stale === "string")
                    message.stale = $parseInt(object.stale, 10);
                else if (typeof object.stale === "number")
                    message.stale = object.stale;
                else if (typeof object.stale === "object")
                    message.stale = new $util.LongBits(object.stale.low >>> 0, object.stale.high >>> 0).toNumber(true);
        if (object.start != null)
            if (typeof object.start === "object" ? object.start.low || object.start.high : $Number(object.start) !== 0)
                if ($util.Long)
                    message.start = $util.Long.fromValue(object.start, true);
                else if (typeof object.start === "string")
                    message.start = $parseInt(object.start, 10);
                else if (typeof object.start === "number")
                    message.start = object.start;
                else if (typeof object.start === "object")
                    message.start = new $util.LongBits(object.start.low >>> 0, object.start.high >>> 0).toNumber(true);
        if (object.time != null)
            if (typeof object.time === "object" ? object.time.low || object.time.high : $Number(object.time) !== 0)
                if ($util.Long)
                    message.time = $util.Long.fromValue(object.time, true);
                else if (typeof object.time === "string")
                    message.time = $parseInt(object.time, 10);
                else if (typeof object.time === "number")
                    message.time = object.time;
                else if (typeof object.time === "object")
                    message.time = new $util.LongBits(object.time.low >>> 0, object.time.high >>> 0).toNumber(true);
        if (object.values) {
            if (!$util.isObject(object.values))
                throw $TypeError(".ElementMessage.values: object expected");
            message.values = {};
            for (let keys = $Object.keys(object.values), i = 0; i < keys.length; ++i) {
                if (keys[i] === "__proto__")
                    $util.makeProp(message.values, keys[i]);
                if (!$util.isObject(object.values[keys[i]]))
                    throw $TypeError(".ElementMessage.values: object expected");
                message.values[keys[i]] = $root.Value.fromObject(object.values[keys[i]], _depth + 1);
            }
        }
        return message;
    };

    /**
     * Creates a plain object from an ElementMessage message. Also converts values to other types if specified.
     * @function toObject
     * @memberof ElementMessage
     * @static
     * @param {ElementMessage} message ElementMessage
     * @param {$protobuf.IConversionOptions} [options] Conversion options
     * @returns {Object.<string,*>} Plain object
     */
    ElementMessage.toObject = function (message, options, _depth) {
        if (!options)
            options = {};
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $util.recursionLimit)
            throw $Error("max depth exceeded");
        let object = {};
        if (options.arrays || options.defaults)
            object.positions = [];
        if (options.objects || options.defaults)
            object.values = {};
        if (options.defaults) {
            object.type = "";
            if (options.bytes === $String)
                object.id = "";
            else {
                object.id = [];
                if (options.bytes !== $Array)
                    object.id = $util.newBuffer(object.id);
            }
            object.objectId = "";
            object.displayName = "";
            if ($util.Long) {
                let long = new $util.Long(0, 0, true);
                object.stale = options.longs === $String ? long.toString() : options.longs === $Number ? long.toNumber() : typeof $BigInt !== "undefined" && options.longs === $BigInt ? long.toBigInt() : long;
            } else
                object.stale = options.longs === $String ? "0" : typeof $BigInt !== "undefined" && options.longs === $BigInt ? $BigInt("0") : 0;
            if (options.bytes === $String)
                object.source = "";
            else {
                object.source = [];
                if (options.bytes !== $Array)
                    object.source = $util.newBuffer(object.source);
            }
            if ($util.Long) {
                let long = new $util.Long(0, 0, true);
                object.start = options.longs === $String ? long.toString() : options.longs === $Number ? long.toNumber() : typeof $BigInt !== "undefined" && options.longs === $BigInt ? long.toBigInt() : long;
            } else
                object.start = options.longs === $String ? "0" : typeof $BigInt !== "undefined" && options.longs === $BigInt ? $BigInt("0") : 0;
            if ($util.Long) {
                let long = new $util.Long(0, 0, true);
                object.time = options.longs === $String ? long.toString() : options.longs === $Number ? long.toNumber() : typeof $BigInt !== "undefined" && options.longs === $BigInt ? long.toBigInt() : long;
            } else
                object.time = options.longs === $String ? "0" : typeof $BigInt !== "undefined" && options.longs === $BigInt ? $BigInt("0") : 0;
        }
        if (message.type != null && $Object.hasOwnProperty.call(message, "type"))
            object.type = message.type;
        if (message.id != null && $Object.hasOwnProperty.call(message, "id"))
            object.id = options.bytes === $String ? $util.base64.encode(message.id, 0, message.id.length) : options.bytes === $Array ? $Array.prototype.slice.call(message.id) : message.id;
        if (message.objectId != null && $Object.hasOwnProperty.call(message, "objectId"))
            object.objectId = message.objectId;
        if (message.displayName != null && $Object.hasOwnProperty.call(message, "displayName"))
            object.displayName = message.displayName;
        if (message.positions && message.positions.length) {
            object.positions = $Array(message.positions.length);
            for (let j = 0; j < message.positions.length; ++j)
                object.positions[j] = options.json && !$isFinite(message.positions[j]) ? $String(message.positions[j]) : message.positions[j];
        }
        if (message.stale != null && $Object.hasOwnProperty.call(message, "stale"))
            if (typeof $BigInt !== "undefined" && options.longs === $BigInt)
                object.stale = typeof message.stale === "number" ? $BigInt(message.stale) : $util.Long.fromBits(message.stale.low >>> 0, message.stale.high >>> 0, true).toBigInt();
            else if (typeof message.stale === "number")
                object.stale = options.longs === $String ? $String(message.stale) : message.stale;
            else
                object.stale = options.longs === $String ? $util.Long.prototype.toString.call(message.stale) : options.longs === $Number ? new $util.LongBits(message.stale.low >>> 0, message.stale.high >>> 0).toNumber(true) : message.stale;
        let keys2;
        if (message.values && (keys2 = $Object.keys(message.values)).length) {
            object.values = {};
            for (let j = 0; j < keys2.length; ++j) {
                if (keys2[j] === "__proto__")
                    $util.makeProp(object.values, keys2[j]);
                object.values[keys2[j]] = $root.Value.toObject(message.values[keys2[j]], options, _depth + 1);
            }
        }
        if (message.source != null && $Object.hasOwnProperty.call(message, "source"))
            object.source = options.bytes === $String ? $util.base64.encode(message.source, 0, message.source.length) : options.bytes === $Array ? $Array.prototype.slice.call(message.source) : message.source;
        if (message.start != null && $Object.hasOwnProperty.call(message, "start"))
            if (typeof $BigInt !== "undefined" && options.longs === $BigInt)
                object.start = typeof message.start === "number" ? $BigInt(message.start) : $util.Long.fromBits(message.start.low >>> 0, message.start.high >>> 0, true).toBigInt();
            else if (typeof message.start === "number")
                object.start = options.longs === $String ? $String(message.start) : message.start;
            else
                object.start = options.longs === $String ? $util.Long.prototype.toString.call(message.start) : options.longs === $Number ? new $util.LongBits(message.start.low >>> 0, message.start.high >>> 0).toNumber(true) : message.start;
        if (message.time != null && $Object.hasOwnProperty.call(message, "time"))
            if (typeof $BigInt !== "undefined" && options.longs === $BigInt)
                object.time = typeof message.time === "number" ? $BigInt(message.time) : $util.Long.fromBits(message.time.low >>> 0, message.time.high >>> 0, true).toBigInt();
            else if (typeof message.time === "number")
                object.time = options.longs === $String ? $String(message.time) : message.time;
            else
                object.time = options.longs === $String ? $util.Long.prototype.toString.call(message.time) : options.longs === $Number ? new $util.LongBits(message.time.low >>> 0, message.time.high >>> 0).toNumber(true) : message.time;
        return object;
    };

    /**
     * Converts this ElementMessage to JSON.
     * @function toJSON
     * @memberof ElementMessage
     * @instance
     * @returns {Object.<string,*>} JSON object
     */
    ElementMessage.prototype.toJSON = function() {
        return ElementMessage.toObject(this, $protobuf.util.toJSONOptions);
    };

    /**
     * Gets the type url for ElementMessage
     * @function getTypeUrl
     * @memberof ElementMessage
     * @static
     * @param {string} [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
     * @returns {string} The type url
     */
    ElementMessage.getTypeUrl = function(prefix) {
        if (prefix === $undefined)
            prefix = "type.googleapis.com";
        return prefix + "/ElementMessage";
    };

    return ElementMessage;
})();

/**
 * HistoricalChunkResolution enum.
 * @name HistoricalChunkResolution
 * @enum {number}
 * @property {number} HISTORICAL_CHUNK_RESOLUTION_UNKNOWN=0 HISTORICAL_CHUNK_RESOLUTION_UNKNOWN value
 * @property {number} HISTORICAL_CHUNK_RESOLUTION_MINUTE_1=1 HISTORICAL_CHUNK_RESOLUTION_MINUTE_1 value
 * @property {number} HISTORICAL_CHUNK_RESOLUTION_HOUR_1=2 HISTORICAL_CHUNK_RESOLUTION_HOUR_1 value
 * @property {number} HISTORICAL_CHUNK_RESOLUTION_DAY_1=3 HISTORICAL_CHUNK_RESOLUTION_DAY_1 value
 */
export const HistoricalChunkResolution = $root.HistoricalChunkResolution = (() => {
    const valuesById = {}, values = $Object.create(valuesById);
    values[valuesById[0] = "HISTORICAL_CHUNK_RESOLUTION_UNKNOWN"] = 0;
    values[valuesById[1] = "HISTORICAL_CHUNK_RESOLUTION_MINUTE_1"] = 1;
    values[valuesById[2] = "HISTORICAL_CHUNK_RESOLUTION_HOUR_1"] = 2;
    values[valuesById[3] = "HISTORICAL_CHUNK_RESOLUTION_DAY_1"] = 3;
    return values;
})();

/**
 * HistoricalChunkProfile enum.
 * @name HistoricalChunkProfile
 * @enum {number}
 * @property {number} HISTORICAL_CHUNK_PROFILE_UNKNOWN=0 HISTORICAL_CHUNK_PROFILE_UNKNOWN value
 * @property {number} HISTORICAL_CHUNK_PROFILE_DETAIL=1 HISTORICAL_CHUNK_PROFILE_DETAIL value
 */
export const HistoricalChunkProfile = $root.HistoricalChunkProfile = (() => {
    const valuesById = {}, values = $Object.create(valuesById);
    values[valuesById[0] = "HISTORICAL_CHUNK_PROFILE_UNKNOWN"] = 0;
    values[valuesById[1] = "HISTORICAL_CHUNK_PROFILE_DETAIL"] = 1;
    return values;
})();

export const HistoricalChunkPayload = $root.HistoricalChunkPayload = (() => {

    /**
     * Properties of a HistoricalChunkPayload.
     * @typedef {Object} HistoricalChunkPayload.$Properties
     * @property {string|null} [chunkKey] HistoricalChunkPayload chunkKey
     * @property {number|null} [chunkIndex] HistoricalChunkPayload chunkIndex
     * @property {number|Long|null} [fromEpochMillis] HistoricalChunkPayload fromEpochMillis
     * @property {number|Long|null} [toEpochMillis] HistoricalChunkPayload toEpochMillis
     * @property {HistoricalChunkResolution|null} [resolution] HistoricalChunkPayload resolution
     * @property {HistoricalChunkProfile|null} [profile] HistoricalChunkPayload profile
     * @property {number|null} [pointCount] HistoricalChunkPayload pointCount
     * @property {number|null} [objectCount] HistoricalChunkPayload objectCount
     * @property {Array.<ElementMessage.$Properties>|null} [elements] HistoricalChunkPayload elements
     * @property {number|null} [partIndex] HistoricalChunkPayload partIndex
     * @property {number|null} [partCount] HistoricalChunkPayload partCount
     * @property {boolean|null} [finalForChunk] HistoricalChunkPayload finalForChunk
     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
     */

    /**
     * Properties of a HistoricalChunkPayload.
     * @name IHistoricalChunkPayload
     * @interface IHistoricalChunkPayload
     * @augments HistoricalChunkPayload.$Properties
     * @deprecated Use HistoricalChunkPayload.$Properties instead.
     */

    /**
     * Shape of a HistoricalChunkPayload.
     * @typedef {{
     *   chunkKey?: string|null;
     *   chunkIndex?: number|null;
     *   fromEpochMillis?: number|Long|null;
     *   toEpochMillis?: number|Long|null;
     *   resolution?: HistoricalChunkResolution|null;
     *   profile?: HistoricalChunkProfile|null;
     *   pointCount?: number|null;
     *   objectCount?: number|null;
     *   elements?: Array.<ElementMessage.$Shape>|null;
     *   partIndex?: number|null;
     *   partCount?: number|null;
     *   finalForChunk?: boolean|null;
     *   $unknowns?: Array.<Uint8Array>;
     * }} HistoricalChunkPayload.$Shape
     */

    /**
     * Constructs a new HistoricalChunkPayload.
     * @name HistoricalChunkPayload
     * @classdesc Represents a HistoricalChunkPayload.
     * @constructor
     * @param {HistoricalChunkPayload.$Properties=} [properties] Properties to set
     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
     */
    const HistoricalChunkPayload = function (properties) {
        this.elements = [];
        if (properties)
            for (let keys = $Object.keys(properties), i = 0; i < keys.length; ++i)
                if (properties[keys[i]] != null && keys[i] !== "__proto__")
                    this[keys[i]] = properties[keys[i]];
    };

    /**
     * HistoricalChunkPayload chunkKey.
     * @member {string} chunkKey
     * @memberof HistoricalChunkPayload
     * @instance
     */
    HistoricalChunkPayload.prototype.chunkKey = "";

    /**
     * HistoricalChunkPayload chunkIndex.
     * @member {number} chunkIndex
     * @memberof HistoricalChunkPayload
     * @instance
     */
    HistoricalChunkPayload.prototype.chunkIndex = 0;

    /**
     * HistoricalChunkPayload fromEpochMillis.
     * @member {number|Long} fromEpochMillis
     * @memberof HistoricalChunkPayload
     * @instance
     */
    HistoricalChunkPayload.prototype.fromEpochMillis = $util.Long ? $util.Long.fromBits(0,0,true) : 0;

    /**
     * HistoricalChunkPayload toEpochMillis.
     * @member {number|Long} toEpochMillis
     * @memberof HistoricalChunkPayload
     * @instance
     */
    HistoricalChunkPayload.prototype.toEpochMillis = $util.Long ? $util.Long.fromBits(0,0,true) : 0;

    /**
     * HistoricalChunkPayload resolution.
     * @member {HistoricalChunkResolution} resolution
     * @memberof HistoricalChunkPayload
     * @instance
     */
    HistoricalChunkPayload.prototype.resolution = 0;

    /**
     * HistoricalChunkPayload profile.
     * @member {HistoricalChunkProfile} profile
     * @memberof HistoricalChunkPayload
     * @instance
     */
    HistoricalChunkPayload.prototype.profile = 0;

    /**
     * HistoricalChunkPayload pointCount.
     * @member {number} pointCount
     * @memberof HistoricalChunkPayload
     * @instance
     */
    HistoricalChunkPayload.prototype.pointCount = 0;

    /**
     * HistoricalChunkPayload objectCount.
     * @member {number} objectCount
     * @memberof HistoricalChunkPayload
     * @instance
     */
    HistoricalChunkPayload.prototype.objectCount = 0;

    /**
     * HistoricalChunkPayload elements.
     * @member {Array.<ElementMessage.$Properties>} elements
     * @memberof HistoricalChunkPayload
     * @instance
     */
    HistoricalChunkPayload.prototype.elements = $util.emptyArray;

    /**
     * HistoricalChunkPayload partIndex.
     * @member {number} partIndex
     * @memberof HistoricalChunkPayload
     * @instance
     */
    HistoricalChunkPayload.prototype.partIndex = 0;

    /**
     * HistoricalChunkPayload partCount.
     * @member {number} partCount
     * @memberof HistoricalChunkPayload
     * @instance
     */
    HistoricalChunkPayload.prototype.partCount = 0;

    /**
     * HistoricalChunkPayload finalForChunk.
     * @member {boolean} finalForChunk
     * @memberof HistoricalChunkPayload
     * @instance
     */
    HistoricalChunkPayload.prototype.finalForChunk = false;

    /**
     * Creates a new HistoricalChunkPayload instance using the specified properties.
     * @function create
     * @memberof HistoricalChunkPayload
     * @static
     * @param {HistoricalChunkPayload.$Properties=} [properties] Properties to set
     * @returns {HistoricalChunkPayload} HistoricalChunkPayload instance
     * @type {{
     *   (properties: HistoricalChunkPayload.$Shape): HistoricalChunkPayload & HistoricalChunkPayload.$Shape;
     *   (properties?: HistoricalChunkPayload.$Properties): HistoricalChunkPayload;
     * }}
     */
    HistoricalChunkPayload.create = function(properties) {
        return new HistoricalChunkPayload(properties);
    };

    /**
     * Encodes the specified HistoricalChunkPayload message. Does not implicitly {@link HistoricalChunkPayload.verify|verify} messages.
     * @function encode
     * @memberof HistoricalChunkPayload
     * @static
     * @param {HistoricalChunkPayload.$Properties} message HistoricalChunkPayload message or plain object to encode
     * @param {$protobuf.Writer} [writer] Writer to encode to
     * @returns {$protobuf.Writer} Writer
     */
    HistoricalChunkPayload.encode = function (message, writer, _depth) {
        if (!writer)
            writer = $Writer.create();
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $util.recursionLimit)
            throw $Error("max depth exceeded");
        if (message.chunkKey != null && $Object.hasOwnProperty.call(message, "chunkKey") && message.chunkKey !== "")
            writer.uint32(/* id 1, wireType 2 =*/10).string(message.chunkKey);
        if (message.chunkIndex != null && $Object.hasOwnProperty.call(message, "chunkIndex") && message.chunkIndex !== 0)
            writer.uint32(/* id 2, wireType 0 =*/16).uint32(message.chunkIndex);
        if (message.fromEpochMillis != null && $Object.hasOwnProperty.call(message, "fromEpochMillis") && (typeof message.fromEpochMillis === "object" ? message.fromEpochMillis.low || message.fromEpochMillis.high : message.fromEpochMillis !== 0))
            writer.uint32(/* id 3, wireType 0 =*/24).uint64(message.fromEpochMillis);
        if (message.toEpochMillis != null && $Object.hasOwnProperty.call(message, "toEpochMillis") && (typeof message.toEpochMillis === "object" ? message.toEpochMillis.low || message.toEpochMillis.high : message.toEpochMillis !== 0))
            writer.uint32(/* id 4, wireType 0 =*/32).uint64(message.toEpochMillis);
        if (message.resolution != null && $Object.hasOwnProperty.call(message, "resolution") && message.resolution !== 0)
            writer.uint32(/* id 5, wireType 0 =*/40).int32(message.resolution);
        if (message.profile != null && $Object.hasOwnProperty.call(message, "profile") && message.profile !== 0)
            writer.uint32(/* id 6, wireType 0 =*/48).int32(message.profile);
        if (message.pointCount != null && $Object.hasOwnProperty.call(message, "pointCount") && message.pointCount !== 0)
            writer.uint32(/* id 7, wireType 0 =*/56).uint32(message.pointCount);
        if (message.objectCount != null && $Object.hasOwnProperty.call(message, "objectCount") && message.objectCount !== 0)
            writer.uint32(/* id 8, wireType 0 =*/64).uint32(message.objectCount);
        if (message.elements != null && message.elements.length)
            for (let i = 0; i < message.elements.length; ++i)
                $root.ElementMessage.encode(message.elements[i], writer.uint32(/* id 9, wireType 2 =*/74).fork(), _depth + 1).ldelim();
        if (message.partIndex != null && $Object.hasOwnProperty.call(message, "partIndex") && message.partIndex !== 0)
            writer.uint32(/* id 10, wireType 0 =*/80).uint32(message.partIndex);
        if (message.partCount != null && $Object.hasOwnProperty.call(message, "partCount") && message.partCount !== 0)
            writer.uint32(/* id 11, wireType 0 =*/88).uint32(message.partCount);
        if (message.finalForChunk != null && $Object.hasOwnProperty.call(message, "finalForChunk") && message.finalForChunk !== false)
            writer.uint32(/* id 12, wireType 0 =*/96).bool(message.finalForChunk);
        if (message.$unknowns != null && $Object.hasOwnProperty.call(message, "$unknowns"))
            for (let i = 0; i < message.$unknowns.length; ++i)
                writer.raw(message.$unknowns[i]);
        return writer;
    };

    /**
     * Encodes the specified HistoricalChunkPayload message, length delimited. Does not implicitly {@link HistoricalChunkPayload.verify|verify} messages.
     * @function encodeDelimited
     * @memberof HistoricalChunkPayload
     * @static
     * @param {HistoricalChunkPayload.$Properties} message HistoricalChunkPayload message or plain object to encode
     * @param {$protobuf.Writer} [writer] Writer to encode to
     * @returns {$protobuf.Writer} Writer
     */
    HistoricalChunkPayload.encodeDelimited = function(message, writer) {
        return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
    };

    /**
     * Decodes a HistoricalChunkPayload message from the specified reader or buffer.
     * @function decode
     * @memberof HistoricalChunkPayload
     * @static
     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
     * @param {number} [length] Message length if known beforehand
     * @returns {HistoricalChunkPayload & HistoricalChunkPayload.$Shape} HistoricalChunkPayload
     * @throws {Error} If the payload is not a reader or valid buffer
     * @throws {$protobuf.util.ProtocolError} If required fields are missing
     */
    HistoricalChunkPayload.decode = function (reader, length, _end, _depth, _target) {
        if (!(reader instanceof $Reader))
            reader = $Reader.create(reader);
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $Reader.recursionLimit)
            throw $Error("max depth exceeded");
        let end = length === $undefined ? reader.len : reader.pos + length, message = _target || new $root.HistoricalChunkPayload(), value;
        while (reader.pos < end) {
            let start = reader.pos;
            let tag = reader.tag();
            if (tag === _end) {
                _end = $undefined;
                break;
            }
            let wireType = tag & 7;
            switch (tag >>>= 3) {
            case 1: {
                    if (wireType !== 2)
                        break;
                    if ((value = reader.stringVerify()).length)
                        message.chunkKey = value;
                    else
                        delete message.chunkKey;
                    continue;
                }
            case 2: {
                    if (wireType !== 0)
                        break;
                    if (value = reader.uint32())
                        message.chunkIndex = value;
                    else
                        delete message.chunkIndex;
                    continue;
                }
            case 3: {
                    if (wireType !== 0)
                        break;
                    if (typeof (value = reader.uint64()) === "object" ? value.low || value.high : value !== 0)
                        message.fromEpochMillis = value;
                    else
                        delete message.fromEpochMillis;
                    continue;
                }
            case 4: {
                    if (wireType !== 0)
                        break;
                    if (typeof (value = reader.uint64()) === "object" ? value.low || value.high : value !== 0)
                        message.toEpochMillis = value;
                    else
                        delete message.toEpochMillis;
                    continue;
                }
            case 5: {
                    if (wireType !== 0)
                        break;
                    if (value = reader.int32())
                        message.resolution = value;
                    else
                        delete message.resolution;
                    continue;
                }
            case 6: {
                    if (wireType !== 0)
                        break;
                    if (value = reader.int32())
                        message.profile = value;
                    else
                        delete message.profile;
                    continue;
                }
            case 7: {
                    if (wireType !== 0)
                        break;
                    if (value = reader.uint32())
                        message.pointCount = value;
                    else
                        delete message.pointCount;
                    continue;
                }
            case 8: {
                    if (wireType !== 0)
                        break;
                    if (value = reader.uint32())
                        message.objectCount = value;
                    else
                        delete message.objectCount;
                    continue;
                }
            case 9: {
                    if (wireType !== 2)
                        break;
                    if (!(message.elements && message.elements.length))
                        message.elements = [];
                    message.elements.push($root.ElementMessage.decode(reader, reader.uint32(), $undefined, _depth + 1));
                    continue;
                }
            case 10: {
                    if (wireType !== 0)
                        break;
                    if (value = reader.uint32())
                        message.partIndex = value;
                    else
                        delete message.partIndex;
                    continue;
                }
            case 11: {
                    if (wireType !== 0)
                        break;
                    if (value = reader.uint32())
                        message.partCount = value;
                    else
                        delete message.partCount;
                    continue;
                }
            case 12: {
                    if (wireType !== 0)
                        break;
                    if (value = reader.bool())
                        message.finalForChunk = value;
                    else
                        delete message.finalForChunk;
                    continue;
                }
            }
            reader.skipType(wireType, _depth, tag);
            if (!reader.discardUnknown) {
                $util.makeProp(message, "$unknowns", false);
                (message.$unknowns || (message.$unknowns = [])).push(reader.raw(start, reader.pos));
            }
        }
        if (_end !== $undefined)
            throw $Error("missing end group");
        return message;
    };

    /**
     * Decodes a HistoricalChunkPayload message from the specified reader or buffer, length delimited.
     * @function decodeDelimited
     * @memberof HistoricalChunkPayload
     * @static
     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
     * @returns {HistoricalChunkPayload & HistoricalChunkPayload.$Shape} HistoricalChunkPayload
     * @throws {Error} If the payload is not a reader or valid buffer
     * @throws {$protobuf.util.ProtocolError} If required fields are missing
     */
    HistoricalChunkPayload.decodeDelimited = function(reader) {
        if (!(reader instanceof $Reader))
            reader = new $Reader(reader);
        return this.decode(reader, reader.uint32());
    };

    /**
     * Verifies a HistoricalChunkPayload message.
     * @function verify
     * @memberof HistoricalChunkPayload
     * @static
     * @param {Object.<string,*>} message Plain object to verify
     * @returns {string|null} `null` if valid, otherwise the reason why it is not
     */
    HistoricalChunkPayload.verify = function (message, _depth) {
        if (typeof message !== "object" || message === null)
            return "object expected";
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $util.recursionLimit)
            return "max depth exceeded";
        if (message.chunkKey != null && $Object.hasOwnProperty.call(message, "chunkKey"))
            if (!$util.isString(message.chunkKey))
                return "chunkKey: string expected";
        if (message.chunkIndex != null && $Object.hasOwnProperty.call(message, "chunkIndex"))
            if (!$util.isInteger(message.chunkIndex))
                return "chunkIndex: integer expected";
        if (message.fromEpochMillis != null && $Object.hasOwnProperty.call(message, "fromEpochMillis"))
            if (!$util.isInteger(message.fromEpochMillis) && !(message.fromEpochMillis && $util.isInteger(message.fromEpochMillis.low) && $util.isInteger(message.fromEpochMillis.high)))
                return "fromEpochMillis: integer|Long expected";
        if (message.toEpochMillis != null && $Object.hasOwnProperty.call(message, "toEpochMillis"))
            if (!$util.isInteger(message.toEpochMillis) && !(message.toEpochMillis && $util.isInteger(message.toEpochMillis.low) && $util.isInteger(message.toEpochMillis.high)))
                return "toEpochMillis: integer|Long expected";
        if (message.resolution != null && $Object.hasOwnProperty.call(message, "resolution"))
            if (typeof message.resolution !== "number" || (message.resolution | 0) !== message.resolution)
                return "resolution: enum value expected";
        if (message.profile != null && $Object.hasOwnProperty.call(message, "profile"))
            if (typeof message.profile !== "number" || (message.profile | 0) !== message.profile)
                return "profile: enum value expected";
        if (message.pointCount != null && $Object.hasOwnProperty.call(message, "pointCount"))
            if (!$util.isInteger(message.pointCount))
                return "pointCount: integer expected";
        if (message.objectCount != null && $Object.hasOwnProperty.call(message, "objectCount"))
            if (!$util.isInteger(message.objectCount))
                return "objectCount: integer expected";
        if (message.elements != null && $Object.hasOwnProperty.call(message, "elements")) {
            if (!$Array.isArray(message.elements))
                return "elements: array expected";
            for (let i = 0; i < message.elements.length; ++i) {
                let error = $root.ElementMessage.verify(message.elements[i], _depth + 1);
                if (error)
                    return "elements." + error;
            }
        }
        if (message.partIndex != null && $Object.hasOwnProperty.call(message, "partIndex"))
            if (!$util.isInteger(message.partIndex))
                return "partIndex: integer expected";
        if (message.partCount != null && $Object.hasOwnProperty.call(message, "partCount"))
            if (!$util.isInteger(message.partCount))
                return "partCount: integer expected";
        if (message.finalForChunk != null && $Object.hasOwnProperty.call(message, "finalForChunk"))
            if (typeof message.finalForChunk !== "boolean")
                return "finalForChunk: boolean expected";
        return null;
    };

    /**
     * Creates a HistoricalChunkPayload message from a plain object. Also converts values to their respective internal types.
     * @function fromObject
     * @memberof HistoricalChunkPayload
     * @static
     * @param {Object.<string,*>} object Plain object
     * @returns {HistoricalChunkPayload} HistoricalChunkPayload
     */
    HistoricalChunkPayload.fromObject = function (object, _depth) {
        if (object instanceof $root.HistoricalChunkPayload)
            return object;
        if (!$util.isObject(object))
            throw $TypeError(".HistoricalChunkPayload: object expected");
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $util.recursionLimit)
            throw $Error("max depth exceeded");
        let message = new $root.HistoricalChunkPayload();
        if (object.chunkKey != null)
            if (typeof object.chunkKey !== "string" || object.chunkKey.length)
                message.chunkKey = $String(object.chunkKey);
        if (object.chunkIndex != null)
            if ($Number(object.chunkIndex) !== 0)
                message.chunkIndex = object.chunkIndex >>> 0;
        if (object.fromEpochMillis != null)
            if (typeof object.fromEpochMillis === "object" ? object.fromEpochMillis.low || object.fromEpochMillis.high : $Number(object.fromEpochMillis) !== 0)
                if ($util.Long)
                    message.fromEpochMillis = $util.Long.fromValue(object.fromEpochMillis, true);
                else if (typeof object.fromEpochMillis === "string")
                    message.fromEpochMillis = $parseInt(object.fromEpochMillis, 10);
                else if (typeof object.fromEpochMillis === "number")
                    message.fromEpochMillis = object.fromEpochMillis;
                else if (typeof object.fromEpochMillis === "object")
                    message.fromEpochMillis = new $util.LongBits(object.fromEpochMillis.low >>> 0, object.fromEpochMillis.high >>> 0).toNumber(true);
        if (object.toEpochMillis != null)
            if (typeof object.toEpochMillis === "object" ? object.toEpochMillis.low || object.toEpochMillis.high : $Number(object.toEpochMillis) !== 0)
                if ($util.Long)
                    message.toEpochMillis = $util.Long.fromValue(object.toEpochMillis, true);
                else if (typeof object.toEpochMillis === "string")
                    message.toEpochMillis = $parseInt(object.toEpochMillis, 10);
                else if (typeof object.toEpochMillis === "number")
                    message.toEpochMillis = object.toEpochMillis;
                else if (typeof object.toEpochMillis === "object")
                    message.toEpochMillis = new $util.LongBits(object.toEpochMillis.low >>> 0, object.toEpochMillis.high >>> 0).toNumber(true);
        if (object.resolution !== 0 && (typeof object.resolution !== "string" || $root.HistoricalChunkResolution[object.resolution] !== 0))
            switch (object.resolution) {
            case "HISTORICAL_CHUNK_RESOLUTION_UNKNOWN":
            case 0:
                message.resolution = 0;
                break;
            case "HISTORICAL_CHUNK_RESOLUTION_MINUTE_1":
            case 1:
                message.resolution = 1;
                break;
            case "HISTORICAL_CHUNK_RESOLUTION_HOUR_1":
            case 2:
                message.resolution = 2;
                break;
            case "HISTORICAL_CHUNK_RESOLUTION_DAY_1":
            case 3:
                message.resolution = 3;
                break;
            default:
                if (typeof object.resolution === "number" && (object.resolution | 0) === object.resolution)
                    message.resolution = object.resolution;
            }
        if (object.profile !== 0 && (typeof object.profile !== "string" || $root.HistoricalChunkProfile[object.profile] !== 0))
            switch (object.profile) {
            case "HISTORICAL_CHUNK_PROFILE_UNKNOWN":
            case 0:
                message.profile = 0;
                break;
            case "HISTORICAL_CHUNK_PROFILE_DETAIL":
            case 1:
                message.profile = 1;
                break;
            default:
                if (typeof object.profile === "number" && (object.profile | 0) === object.profile)
                    message.profile = object.profile;
            }
        if (object.pointCount != null)
            if ($Number(object.pointCount) !== 0)
                message.pointCount = object.pointCount >>> 0;
        if (object.objectCount != null)
            if ($Number(object.objectCount) !== 0)
                message.objectCount = object.objectCount >>> 0;
        if (object.elements) {
            if (!$Array.isArray(object.elements))
                throw $TypeError(".HistoricalChunkPayload.elements: array expected");
            message.elements = $Array(object.elements.length);
            for (let i = 0; i < object.elements.length; ++i) {
                if (!$util.isObject(object.elements[i]))
                    throw $TypeError(".HistoricalChunkPayload.elements: object expected");
                message.elements[i] = $root.ElementMessage.fromObject(object.elements[i], _depth + 1);
            }
        }
        if (object.partIndex != null)
            if ($Number(object.partIndex) !== 0)
                message.partIndex = object.partIndex >>> 0;
        if (object.partCount != null)
            if ($Number(object.partCount) !== 0)
                message.partCount = object.partCount >>> 0;
        if (object.finalForChunk != null)
            if (object.finalForChunk)
                message.finalForChunk = $Boolean(object.finalForChunk);
        return message;
    };

    /**
     * Creates a plain object from a HistoricalChunkPayload message. Also converts values to other types if specified.
     * @function toObject
     * @memberof HistoricalChunkPayload
     * @static
     * @param {HistoricalChunkPayload} message HistoricalChunkPayload
     * @param {$protobuf.IConversionOptions} [options] Conversion options
     * @returns {Object.<string,*>} Plain object
     */
    HistoricalChunkPayload.toObject = function (message, options, _depth) {
        if (!options)
            options = {};
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $util.recursionLimit)
            throw $Error("max depth exceeded");
        let object = {};
        if (options.arrays || options.defaults)
            object.elements = [];
        if (options.defaults) {
            object.chunkKey = "";
            object.chunkIndex = 0;
            if ($util.Long) {
                let long = new $util.Long(0, 0, true);
                object.fromEpochMillis = options.longs === $String ? long.toString() : options.longs === $Number ? long.toNumber() : typeof $BigInt !== "undefined" && options.longs === $BigInt ? long.toBigInt() : long;
            } else
                object.fromEpochMillis = options.longs === $String ? "0" : typeof $BigInt !== "undefined" && options.longs === $BigInt ? $BigInt("0") : 0;
            if ($util.Long) {
                let long = new $util.Long(0, 0, true);
                object.toEpochMillis = options.longs === $String ? long.toString() : options.longs === $Number ? long.toNumber() : typeof $BigInt !== "undefined" && options.longs === $BigInt ? long.toBigInt() : long;
            } else
                object.toEpochMillis = options.longs === $String ? "0" : typeof $BigInt !== "undefined" && options.longs === $BigInt ? $BigInt("0") : 0;
            object.resolution = options.enums === $String ? "HISTORICAL_CHUNK_RESOLUTION_UNKNOWN" : 0;
            object.profile = options.enums === $String ? "HISTORICAL_CHUNK_PROFILE_UNKNOWN" : 0;
            object.pointCount = 0;
            object.objectCount = 0;
            object.partIndex = 0;
            object.partCount = 0;
            object.finalForChunk = false;
        }
        if (message.chunkKey != null && $Object.hasOwnProperty.call(message, "chunkKey"))
            object.chunkKey = message.chunkKey;
        if (message.chunkIndex != null && $Object.hasOwnProperty.call(message, "chunkIndex"))
            object.chunkIndex = message.chunkIndex;
        if (message.fromEpochMillis != null && $Object.hasOwnProperty.call(message, "fromEpochMillis"))
            if (typeof $BigInt !== "undefined" && options.longs === $BigInt)
                object.fromEpochMillis = typeof message.fromEpochMillis === "number" ? $BigInt(message.fromEpochMillis) : $util.Long.fromBits(message.fromEpochMillis.low >>> 0, message.fromEpochMillis.high >>> 0, true).toBigInt();
            else if (typeof message.fromEpochMillis === "number")
                object.fromEpochMillis = options.longs === $String ? $String(message.fromEpochMillis) : message.fromEpochMillis;
            else
                object.fromEpochMillis = options.longs === $String ? $util.Long.prototype.toString.call(message.fromEpochMillis) : options.longs === $Number ? new $util.LongBits(message.fromEpochMillis.low >>> 0, message.fromEpochMillis.high >>> 0).toNumber(true) : message.fromEpochMillis;
        if (message.toEpochMillis != null && $Object.hasOwnProperty.call(message, "toEpochMillis"))
            if (typeof $BigInt !== "undefined" && options.longs === $BigInt)
                object.toEpochMillis = typeof message.toEpochMillis === "number" ? $BigInt(message.toEpochMillis) : $util.Long.fromBits(message.toEpochMillis.low >>> 0, message.toEpochMillis.high >>> 0, true).toBigInt();
            else if (typeof message.toEpochMillis === "number")
                object.toEpochMillis = options.longs === $String ? $String(message.toEpochMillis) : message.toEpochMillis;
            else
                object.toEpochMillis = options.longs === $String ? $util.Long.prototype.toString.call(message.toEpochMillis) : options.longs === $Number ? new $util.LongBits(message.toEpochMillis.low >>> 0, message.toEpochMillis.high >>> 0).toNumber(true) : message.toEpochMillis;
        if (message.resolution != null && $Object.hasOwnProperty.call(message, "resolution"))
            object.resolution = options.enums === $String ? $root.HistoricalChunkResolution[message.resolution] === $undefined ? message.resolution : $root.HistoricalChunkResolution[message.resolution] : message.resolution;
        if (message.profile != null && $Object.hasOwnProperty.call(message, "profile"))
            object.profile = options.enums === $String ? $root.HistoricalChunkProfile[message.profile] === $undefined ? message.profile : $root.HistoricalChunkProfile[message.profile] : message.profile;
        if (message.pointCount != null && $Object.hasOwnProperty.call(message, "pointCount"))
            object.pointCount = message.pointCount;
        if (message.objectCount != null && $Object.hasOwnProperty.call(message, "objectCount"))
            object.objectCount = message.objectCount;
        if (message.elements && message.elements.length) {
            object.elements = $Array(message.elements.length);
            for (let j = 0; j < message.elements.length; ++j)
                object.elements[j] = $root.ElementMessage.toObject(message.elements[j], options, _depth + 1);
        }
        if (message.partIndex != null && $Object.hasOwnProperty.call(message, "partIndex"))
            object.partIndex = message.partIndex;
        if (message.partCount != null && $Object.hasOwnProperty.call(message, "partCount"))
            object.partCount = message.partCount;
        if (message.finalForChunk != null && $Object.hasOwnProperty.call(message, "finalForChunk"))
            object.finalForChunk = message.finalForChunk;
        return object;
    };

    /**
     * Converts this HistoricalChunkPayload to JSON.
     * @function toJSON
     * @memberof HistoricalChunkPayload
     * @instance
     * @returns {Object.<string,*>} JSON object
     */
    HistoricalChunkPayload.prototype.toJSON = function() {
        return HistoricalChunkPayload.toObject(this, $protobuf.util.toJSONOptions);
    };

    /**
     * Gets the type url for HistoricalChunkPayload
     * @function getTypeUrl
     * @memberof HistoricalChunkPayload
     * @static
     * @param {string} [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
     * @returns {string} The type url
     */
    HistoricalChunkPayload.getTypeUrl = function(prefix) {
        if (prefix === $undefined)
            prefix = "type.googleapis.com";
        return prefix + "/HistoricalChunkPayload";
    };

    return HistoricalChunkPayload;
})();

export {
  $root as default
};
