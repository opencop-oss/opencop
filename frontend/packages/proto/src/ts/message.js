// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/*eslint-disable block-scoped-var, id-length, no-control-regex, no-magic-numbers, no-mixed-operators, no-prototype-builtins, no-redeclare, no-shadow, no-var, sort-vars, default-case, jsdoc/require-param*/
import $protobuf from "protobufjs/minimal.js";

// Common aliases
const $Reader = $protobuf.Reader, $Writer = $protobuf.Writer, $util = $protobuf.util;
const $Object = $util.global.Object, $undefined = $util.global.undefined, $Error = $util.global.Error, $TypeError = $util.global.TypeError, $String = $util.global.String, $Boolean = $util.global.Boolean, $Array = $util.global.Array;

// Exported root namespace
const $root = $protobuf.roots["default"] || ($protobuf.roots["default"] = {});

export const SocketMessage = $root.SocketMessage = (() => {

    /**
     * Properties of a SocketMessage.
     * @typedef {Object} SocketMessage.$Properties
     * @property {SocketMessage.Type|null} [type] SocketMessage type
     * @property {string|null} [destination] SocketMessage destination
     * @property {Object.<string,string>|null} [headers] SocketMessage headers
     * @property {Uint8Array|null} [payload] SocketMessage payload
     * @property {boolean|null} [compressed] SocketMessage compressed
     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
     */

    /**
     * Properties of a SocketMessage.
     * @name ISocketMessage
     * @interface ISocketMessage
     * @augments SocketMessage.$Properties
     * @deprecated Use SocketMessage.$Properties instead.
     */

    /**
     * Shape of a SocketMessage.
     * @typedef {SocketMessage.$Properties} SocketMessage.$Shape
     */

    /**
     * Constructs a new SocketMessage.
     * @name SocketMessage
     * @classdesc Represents a SocketMessage.
     * @constructor
     * @param {SocketMessage.$Properties=} [properties] Properties to set
     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
     */
    const SocketMessage = function (properties) {
        this.headers = {};
        if (properties)
            for (let keys = $Object.keys(properties), i = 0; i < keys.length; ++i)
                if (properties[keys[i]] != null && keys[i] !== "__proto__")
                    this[keys[i]] = properties[keys[i]];
    };

    /**
     * SocketMessage type.
     * @member {SocketMessage.Type} type
     * @memberof SocketMessage
     * @instance
     */
    SocketMessage.prototype.type = 0;

    /**
     * SocketMessage destination.
     * @member {string} destination
     * @memberof SocketMessage
     * @instance
     */
    SocketMessage.prototype.destination = "";

    /**
     * SocketMessage headers.
     * @member {Object.<string,string>} headers
     * @memberof SocketMessage
     * @instance
     */
    SocketMessage.prototype.headers = $util.emptyObject;

    /**
     * SocketMessage payload.
     * @member {Uint8Array} payload
     * @memberof SocketMessage
     * @instance
     */
    SocketMessage.prototype.payload = $util.newBuffer([]);

    /**
     * SocketMessage compressed.
     * @member {boolean} compressed
     * @memberof SocketMessage
     * @instance
     */
    SocketMessage.prototype.compressed = false;

    /**
     * Creates a new SocketMessage instance using the specified properties.
     * @function create
     * @memberof SocketMessage
     * @static
     * @param {SocketMessage.$Properties=} [properties] Properties to set
     * @returns {SocketMessage} SocketMessage instance
     * @type {{
     *   (properties: SocketMessage.$Shape): SocketMessage & SocketMessage.$Shape;
     *   (properties?: SocketMessage.$Properties): SocketMessage;
     * }}
     */
    SocketMessage.create = function(properties) {
        return new SocketMessage(properties);
    };

    /**
     * Encodes the specified SocketMessage message. Does not implicitly {@link SocketMessage.verify|verify} messages.
     * @function encode
     * @memberof SocketMessage
     * @static
     * @param {SocketMessage.$Properties} message SocketMessage message or plain object to encode
     * @param {$protobuf.Writer} [writer] Writer to encode to
     * @returns {$protobuf.Writer} Writer
     */
    SocketMessage.encode = function (message, writer, _depth) {
        if (!writer)
            writer = $Writer.create();
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $util.recursionLimit)
            throw $Error("max depth exceeded");
        if (message.type != null && $Object.hasOwnProperty.call(message, "type") && message.type !== 0)
            writer.uint32(/* id 1, wireType 0 =*/8).int32(message.type);
        if (message.destination != null && $Object.hasOwnProperty.call(message, "destination") && message.destination !== "")
            writer.uint32(/* id 2, wireType 2 =*/18).string(message.destination);
        if (message.headers != null && $Object.hasOwnProperty.call(message, "headers"))
            for (let keys = $Object.keys(message.headers), i = 0; i < keys.length; ++i)
                writer.uint32(/* id 3, wireType 2 =*/26).fork().uint32(/* id 1, wireType 2 =*/10).string(keys[i]).uint32(/* id 2, wireType 2 =*/18).string(message.headers[keys[i]]).ldelim();
        if (message.payload != null && $Object.hasOwnProperty.call(message, "payload") && message.payload.length)
            writer.uint32(/* id 4, wireType 2 =*/34).bytes(message.payload);
        if (message.compressed != null && $Object.hasOwnProperty.call(message, "compressed") && message.compressed !== false)
            writer.uint32(/* id 5, wireType 0 =*/40).bool(message.compressed);
        if (message.$unknowns != null && $Object.hasOwnProperty.call(message, "$unknowns"))
            for (let i = 0; i < message.$unknowns.length; ++i)
                writer.raw(message.$unknowns[i]);
        return writer;
    };

    /**
     * Encodes the specified SocketMessage message, length delimited. Does not implicitly {@link SocketMessage.verify|verify} messages.
     * @function encodeDelimited
     * @memberof SocketMessage
     * @static
     * @param {SocketMessage.$Properties} message SocketMessage message or plain object to encode
     * @param {$protobuf.Writer} [writer] Writer to encode to
     * @returns {$protobuf.Writer} Writer
     */
    SocketMessage.encodeDelimited = function(message, writer) {
        return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
    };

    /**
     * Decodes a SocketMessage message from the specified reader or buffer.
     * @function decode
     * @memberof SocketMessage
     * @static
     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
     * @param {number} [length] Message length if known beforehand
     * @returns {SocketMessage & SocketMessage.$Shape} SocketMessage
     * @throws {Error} If the payload is not a reader or valid buffer
     * @throws {$protobuf.util.ProtocolError} If required fields are missing
     */
    SocketMessage.decode = function (reader, length, _end, _depth, _target) {
        if (!(reader instanceof $Reader))
            reader = $Reader.create(reader);
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $Reader.recursionLimit)
            throw $Error("max depth exceeded");
        let end = length === $undefined ? reader.len : reader.pos + length, message = _target || new $root.SocketMessage(), key, value;
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
                    if (value = reader.int32())
                        message.type = value;
                    else
                        delete message.type;
                    continue;
                }
            case 2: {
                    if (wireType !== 2)
                        break;
                    if ((value = reader.stringVerify()).length)
                        message.destination = value;
                    else
                        delete message.destination;
                    continue;
                }
            case 3: {
                    if (wireType !== 2)
                        break;
                    if (message.headers === $util.emptyObject)
                        message.headers = {};
                    let end2 = reader.uint32() + reader.pos;
                    key = "";
                    value = "";
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
                            value = reader.stringVerify();
                            continue;
                        }
                        reader.skipType(wireType, _depth, tag2);
                    }
                    if (key === "__proto__")
                        $util.makeProp(message.headers, key);
                    message.headers[key] = value;
                    continue;
                }
            case 4: {
                    if (wireType !== 2)
                        break;
                    if ((value = reader.bytes()).length)
                        message.payload = value;
                    else
                        delete message.payload;
                    continue;
                }
            case 5: {
                    if (wireType !== 0)
                        break;
                    if (value = reader.bool())
                        message.compressed = value;
                    else
                        delete message.compressed;
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
     * Decodes a SocketMessage message from the specified reader or buffer, length delimited.
     * @function decodeDelimited
     * @memberof SocketMessage
     * @static
     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
     * @returns {SocketMessage & SocketMessage.$Shape} SocketMessage
     * @throws {Error} If the payload is not a reader or valid buffer
     * @throws {$protobuf.util.ProtocolError} If required fields are missing
     */
    SocketMessage.decodeDelimited = function(reader) {
        if (!(reader instanceof $Reader))
            reader = new $Reader(reader);
        return this.decode(reader, reader.uint32());
    };

    /**
     * Verifies a SocketMessage message.
     * @function verify
     * @memberof SocketMessage
     * @static
     * @param {Object.<string,*>} message Plain object to verify
     * @returns {string|null} `null` if valid, otherwise the reason why it is not
     */
    SocketMessage.verify = function (message, _depth) {
        if (typeof message !== "object" || message === null)
            return "object expected";
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $util.recursionLimit)
            return "max depth exceeded";
        if (message.type != null && $Object.hasOwnProperty.call(message, "type"))
            if (typeof message.type !== "number" || (message.type | 0) !== message.type)
                return "type: enum value expected";
        if (message.destination != null && $Object.hasOwnProperty.call(message, "destination"))
            if (!$util.isString(message.destination))
                return "destination: string expected";
        if (message.headers != null && $Object.hasOwnProperty.call(message, "headers")) {
            if (!$util.isObject(message.headers))
                return "headers: object expected";
            let key = $Object.keys(message.headers);
            for (let i = 0; i < key.length; ++i)
                if (!$util.isString(message.headers[key[i]]))
                    return "headers: string{k:string} expected";
        }
        if (message.payload != null && $Object.hasOwnProperty.call(message, "payload"))
            if (!(message.payload && typeof message.payload.length === "number" || $util.isString(message.payload)))
                return "payload: buffer expected";
        if (message.compressed != null && $Object.hasOwnProperty.call(message, "compressed"))
            if (typeof message.compressed !== "boolean")
                return "compressed: boolean expected";
        return null;
    };

    /**
     * Creates a SocketMessage message from a plain object. Also converts values to their respective internal types.
     * @function fromObject
     * @memberof SocketMessage
     * @static
     * @param {Object.<string,*>} object Plain object
     * @returns {SocketMessage} SocketMessage
     */
    SocketMessage.fromObject = function (object, _depth) {
        if (object instanceof $root.SocketMessage)
            return object;
        if (!$util.isObject(object))
            throw $TypeError(".SocketMessage: object expected");
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $util.recursionLimit)
            throw $Error("max depth exceeded");
        let message = new $root.SocketMessage();
        if (object.type !== 0 && (typeof object.type !== "string" || $root.SocketMessage.Type[object.type] !== 0))
            switch (object.type) {
            case "UNKNOWN":
            case 0:
                message.type = 0;
                break;
            case "CONNECT":
            case 1:
                message.type = 1;
                break;
            case "SUBSCRIBE":
            case 2:
                message.type = 2;
                break;
            case "SEND":
            case 3:
                message.type = 3;
                break;
            case "UNSUBSCRIBE":
            case 4:
                message.type = 4;
                break;
            case "HEARTBEAT":
            case 5:
                message.type = 5;
                break;
            case "ERROR":
            case 6:
                message.type = 6;
                break;
            default:
                if (typeof object.type === "number" && (object.type | 0) === object.type)
                    message.type = object.type;
            }
        if (object.destination != null)
            if (typeof object.destination !== "string" || object.destination.length)
                message.destination = $String(object.destination);
        if (object.headers) {
            if (!$util.isObject(object.headers))
                throw $TypeError(".SocketMessage.headers: object expected");
            message.headers = {};
            for (let keys = $Object.keys(object.headers), i = 0; i < keys.length; ++i) {
                if (keys[i] === "__proto__")
                    $util.makeProp(message.headers, keys[i]);
                message.headers[keys[i]] = $String(object.headers[keys[i]]);
            }
        }
        if (object.payload != null)
            if (object.payload.length)
                if (typeof object.payload === "string")
                    $util.base64.decode(object.payload, message.payload = $util.newBuffer($util.base64.length(object.payload)), 0);
                else if (object.payload.length >= 0)
                    message.payload = object.payload;
        if (object.compressed != null)
            if (object.compressed)
                message.compressed = $Boolean(object.compressed);
        return message;
    };

    /**
     * Creates a plain object from a SocketMessage message. Also converts values to other types if specified.
     * @function toObject
     * @memberof SocketMessage
     * @static
     * @param {SocketMessage} message SocketMessage
     * @param {$protobuf.IConversionOptions} [options] Conversion options
     * @returns {Object.<string,*>} Plain object
     */
    SocketMessage.toObject = function (message, options, _depth) {
        if (!options)
            options = {};
        if (_depth === $undefined)
            _depth = 0;
        if (_depth > $util.recursionLimit)
            throw $Error("max depth exceeded");
        let object = {};
        if (options.objects || options.defaults)
            object.headers = {};
        if (options.defaults) {
            object.type = options.enums === $String ? "UNKNOWN" : 0;
            object.destination = "";
            if (options.bytes === $String)
                object.payload = "";
            else {
                object.payload = [];
                if (options.bytes !== $Array)
                    object.payload = $util.newBuffer(object.payload);
            }
            object.compressed = false;
        }
        if (message.type != null && $Object.hasOwnProperty.call(message, "type"))
            object.type = options.enums === $String ? $root.SocketMessage.Type[message.type] === $undefined ? message.type : $root.SocketMessage.Type[message.type] : message.type;
        if (message.destination != null && $Object.hasOwnProperty.call(message, "destination"))
            object.destination = message.destination;
        let keys2;
        if (message.headers && (keys2 = $Object.keys(message.headers)).length) {
            object.headers = {};
            for (let j = 0; j < keys2.length; ++j) {
                if (keys2[j] === "__proto__")
                    $util.makeProp(object.headers, keys2[j]);
                object.headers[keys2[j]] = message.headers[keys2[j]];
            }
        }
        if (message.payload != null && $Object.hasOwnProperty.call(message, "payload"))
            object.payload = options.bytes === $String ? $util.base64.encode(message.payload, 0, message.payload.length) : options.bytes === $Array ? $Array.prototype.slice.call(message.payload) : message.payload;
        if (message.compressed != null && $Object.hasOwnProperty.call(message, "compressed"))
            object.compressed = message.compressed;
        return object;
    };

    /**
     * Converts this SocketMessage to JSON.
     * @function toJSON
     * @memberof SocketMessage
     * @instance
     * @returns {Object.<string,*>} JSON object
     */
    SocketMessage.prototype.toJSON = function() {
        return SocketMessage.toObject(this, $protobuf.util.toJSONOptions);
    };

    /**
     * Gets the type url for SocketMessage
     * @function getTypeUrl
     * @memberof SocketMessage
     * @static
     * @param {string} [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
     * @returns {string} The type url
     */
    SocketMessage.getTypeUrl = function(prefix) {
        if (prefix === $undefined)
            prefix = "type.googleapis.com";
        return prefix + "/SocketMessage";
    };

    /**
     * Type enum.
     * @name SocketMessage.Type
     * @enum {number}
     * @property {number} UNKNOWN=0 UNKNOWN value
     * @property {number} CONNECT=1 CONNECT value
     * @property {number} SUBSCRIBE=2 SUBSCRIBE value
     * @property {number} SEND=3 SEND value
     * @property {number} UNSUBSCRIBE=4 UNSUBSCRIBE value
     * @property {number} HEARTBEAT=5 HEARTBEAT value
     * @property {number} ERROR=6 ERROR value
     */
    SocketMessage.Type = (function() {
        const valuesById = {}, values = $Object.create(valuesById);
        values[valuesById[0] = "UNKNOWN"] = 0;
        values[valuesById[1] = "CONNECT"] = 1;
        values[valuesById[2] = "SUBSCRIBE"] = 2;
        values[valuesById[3] = "SEND"] = 3;
        values[valuesById[4] = "UNSUBSCRIBE"] = 4;
        values[valuesById[5] = "HEARTBEAT"] = 5;
        values[valuesById[6] = "ERROR"] = 6;
        return values;
    })();

    SocketMessage.MultiPayload = (function() {

        /**
         * Properties of a MultiPayload.
         * @typedef {Object} SocketMessage.MultiPayload.$Properties
         * @property {Array.<Uint8Array>|null} [payload] MultiPayload payload
         * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
         */

        /**
         * Properties of a MultiPayload.
         * @memberof SocketMessage
         * @interface IMultiPayload
         * @augments SocketMessage.MultiPayload.$Properties
         * @deprecated Use SocketMessage.MultiPayload.$Properties instead.
         */

        /**
         * Shape of a MultiPayload.
         * @typedef {SocketMessage.MultiPayload.$Properties} SocketMessage.MultiPayload.$Shape
         */

        /**
         * Constructs a new MultiPayload.
         * @memberof SocketMessage
         * @classdesc Represents a MultiPayload.
         * @constructor
         * @param {SocketMessage.MultiPayload.$Properties=} [properties] Properties to set
         * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
         */
        const MultiPayload = function (properties) {
            this.payload = [];
            if (properties)
                for (let keys = $Object.keys(properties), i = 0; i < keys.length; ++i)
                    if (properties[keys[i]] != null && keys[i] !== "__proto__")
                        this[keys[i]] = properties[keys[i]];
        };

        /**
         * MultiPayload payload.
         * @member {Array.<Uint8Array>} payload
         * @memberof SocketMessage.MultiPayload
         * @instance
         */
        MultiPayload.prototype.payload = $util.emptyArray;

        /**
         * Creates a new MultiPayload instance using the specified properties.
         * @function create
         * @memberof SocketMessage.MultiPayload
         * @static
         * @param {SocketMessage.MultiPayload.$Properties=} [properties] Properties to set
         * @returns {SocketMessage.MultiPayload} MultiPayload instance
         * @type {{
         *   (properties: SocketMessage.MultiPayload.$Shape): SocketMessage.MultiPayload & SocketMessage.MultiPayload.$Shape;
         *   (properties?: SocketMessage.MultiPayload.$Properties): SocketMessage.MultiPayload;
         * }}
         */
        MultiPayload.create = function(properties) {
            return new MultiPayload(properties);
        };

        /**
         * Encodes the specified MultiPayload message. Does not implicitly {@link SocketMessage.MultiPayload.verify|verify} messages.
         * @function encode
         * @memberof SocketMessage.MultiPayload
         * @static
         * @param {SocketMessage.MultiPayload.$Properties} message MultiPayload message or plain object to encode
         * @param {$protobuf.Writer} [writer] Writer to encode to
         * @returns {$protobuf.Writer} Writer
         */
        MultiPayload.encode = function (message, writer, _depth) {
            if (!writer)
                writer = $Writer.create();
            if (_depth === $undefined)
                _depth = 0;
            if (_depth > $util.recursionLimit)
                throw $Error("max depth exceeded");
            if (message.payload != null && message.payload.length)
                for (let i = 0; i < message.payload.length; ++i)
                    writer.uint32(/* id 1, wireType 2 =*/10).bytes(message.payload[i]);
            if (message.$unknowns != null && $Object.hasOwnProperty.call(message, "$unknowns"))
                for (let i = 0; i < message.$unknowns.length; ++i)
                    writer.raw(message.$unknowns[i]);
            return writer;
        };

        /**
         * Encodes the specified MultiPayload message, length delimited. Does not implicitly {@link SocketMessage.MultiPayload.verify|verify} messages.
         * @function encodeDelimited
         * @memberof SocketMessage.MultiPayload
         * @static
         * @param {SocketMessage.MultiPayload.$Properties} message MultiPayload message or plain object to encode
         * @param {$protobuf.Writer} [writer] Writer to encode to
         * @returns {$protobuf.Writer} Writer
         */
        MultiPayload.encodeDelimited = function(message, writer) {
            return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
        };

        /**
         * Decodes a MultiPayload message from the specified reader or buffer.
         * @function decode
         * @memberof SocketMessage.MultiPayload
         * @static
         * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
         * @param {number} [length] Message length if known beforehand
         * @returns {SocketMessage.MultiPayload & SocketMessage.MultiPayload.$Shape} MultiPayload
         * @throws {Error} If the payload is not a reader or valid buffer
         * @throws {$protobuf.util.ProtocolError} If required fields are missing
         */
        MultiPayload.decode = function (reader, length, _end, _depth, _target) {
            if (!(reader instanceof $Reader))
                reader = $Reader.create(reader);
            if (_depth === $undefined)
                _depth = 0;
            if (_depth > $Reader.recursionLimit)
                throw $Error("max depth exceeded");
            let end = length === $undefined ? reader.len : reader.pos + length, message = _target || new $root.SocketMessage.MultiPayload();
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
                        if (!(message.payload && message.payload.length))
                            message.payload = [];
                        message.payload.push(reader.bytes());
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
         * Decodes a MultiPayload message from the specified reader or buffer, length delimited.
         * @function decodeDelimited
         * @memberof SocketMessage.MultiPayload
         * @static
         * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
         * @returns {SocketMessage.MultiPayload & SocketMessage.MultiPayload.$Shape} MultiPayload
         * @throws {Error} If the payload is not a reader or valid buffer
         * @throws {$protobuf.util.ProtocolError} If required fields are missing
         */
        MultiPayload.decodeDelimited = function(reader) {
            if (!(reader instanceof $Reader))
                reader = new $Reader(reader);
            return this.decode(reader, reader.uint32());
        };

        /**
         * Verifies a MultiPayload message.
         * @function verify
         * @memberof SocketMessage.MultiPayload
         * @static
         * @param {Object.<string,*>} message Plain object to verify
         * @returns {string|null} `null` if valid, otherwise the reason why it is not
         */
        MultiPayload.verify = function (message, _depth) {
            if (typeof message !== "object" || message === null)
                return "object expected";
            if (_depth === $undefined)
                _depth = 0;
            if (_depth > $util.recursionLimit)
                return "max depth exceeded";
            if (message.payload != null && $Object.hasOwnProperty.call(message, "payload")) {
                if (!$Array.isArray(message.payload))
                    return "payload: array expected";
                for (let i = 0; i < message.payload.length; ++i)
                    if (!(message.payload[i] && typeof message.payload[i].length === "number" || $util.isString(message.payload[i])))
                        return "payload: buffer[] expected";
            }
            return null;
        };

        /**
         * Creates a MultiPayload message from a plain object. Also converts values to their respective internal types.
         * @function fromObject
         * @memberof SocketMessage.MultiPayload
         * @static
         * @param {Object.<string,*>} object Plain object
         * @returns {SocketMessage.MultiPayload} MultiPayload
         */
        MultiPayload.fromObject = function (object, _depth) {
            if (object instanceof $root.SocketMessage.MultiPayload)
                return object;
            if (!$util.isObject(object))
                throw $TypeError(".SocketMessage.MultiPayload: object expected");
            if (_depth === $undefined)
                _depth = 0;
            if (_depth > $util.recursionLimit)
                throw $Error("max depth exceeded");
            let message = new $root.SocketMessage.MultiPayload();
            if (object.payload) {
                if (!$Array.isArray(object.payload))
                    throw $TypeError(".SocketMessage.MultiPayload.payload: array expected");
                message.payload = $Array(object.payload.length);
                for (let i = 0; i < object.payload.length; ++i)
                    if (typeof object.payload[i] === "string")
                        $util.base64.decode(object.payload[i], message.payload[i] = $util.newBuffer($util.base64.length(object.payload[i])), 0);
                    else if (object.payload[i].length >= 0)
                        message.payload[i] = object.payload[i];
            }
            return message;
        };

        /**
         * Creates a plain object from a MultiPayload message. Also converts values to other types if specified.
         * @function toObject
         * @memberof SocketMessage.MultiPayload
         * @static
         * @param {SocketMessage.MultiPayload} message MultiPayload
         * @param {$protobuf.IConversionOptions} [options] Conversion options
         * @returns {Object.<string,*>} Plain object
         */
        MultiPayload.toObject = function (message, options, _depth) {
            if (!options)
                options = {};
            if (_depth === $undefined)
                _depth = 0;
            if (_depth > $util.recursionLimit)
                throw $Error("max depth exceeded");
            let object = {};
            if (options.arrays || options.defaults)
                object.payload = [];
            if (message.payload && message.payload.length) {
                object.payload = $Array(message.payload.length);
                for (let j = 0; j < message.payload.length; ++j)
                    object.payload[j] = options.bytes === $String ? $util.base64.encode(message.payload[j], 0, message.payload[j].length) : options.bytes === $Array ? $Array.prototype.slice.call(message.payload[j]) : message.payload[j];
            }
            return object;
        };

        /**
         * Converts this MultiPayload to JSON.
         * @function toJSON
         * @memberof SocketMessage.MultiPayload
         * @instance
         * @returns {Object.<string,*>} JSON object
         */
        MultiPayload.prototype.toJSON = function() {
            return MultiPayload.toObject(this, $protobuf.util.toJSONOptions);
        };

        /**
         * Gets the type url for MultiPayload
         * @function getTypeUrl
         * @memberof SocketMessage.MultiPayload
         * @static
         * @param {string} [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
         * @returns {string} The type url
         */
        MultiPayload.getTypeUrl = function(prefix) {
            if (prefix === $undefined)
                prefix = "type.googleapis.com";
            return prefix + "/SocketMessage.MultiPayload";
        };

        return MultiPayload;
    })();

    return SocketMessage;
})();

export {
  $root as default
};
