// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { AngleMapper } from "./mappers/AngleMapper";
import { ArrowMapper } from "./mappers/ArrowMapper";
import { CircleMapper } from "./mappers/CircleMapper";
import { EllipseMapper } from "./mappers/EllipseMapper";
import { GraphicMapper } from "./mappers/GraphicMapper";
import { LineMapper } from "./mappers/LineMapper";
import { MediaIconMapper } from "./mappers/MediaIconMapper";
import { PointMapper } from "./mappers/PointMapper";
import { PolygonMapper } from "./mappers/PolygonMapper";
import { RectangleMapper } from "./mappers/RectangleMapper";
import { SymbolMapper } from "./mappers/SymbolMapper";
import { TextBoxMapper } from "./mappers/TextBoxMapper";
import { MessageProcessor } from "./MessageProcessor";

let instance: MessageProcessor | null = null;

export function getProcessor(): MessageProcessor {
    if (!instance) {
        instance = new MessageProcessor()
            .register("symbol", new SymbolMapper())
            .register("media-icon", new MediaIconMapper())
            .register("polygon", new PolygonMapper())
            .register("line", new LineMapper())
            .register("ellipse", new EllipseMapper())
            .register("arrow", new ArrowMapper())
            .register("rectangle", new RectangleMapper())
            .register("point", new PointMapper())
            .register("angle", new AngleMapper())
            .register("textbox", new TextBoxMapper())
            .register("circle", new CircleMapper())
            .register("graphic", new GraphicMapper());
    }
    return instance;
}
