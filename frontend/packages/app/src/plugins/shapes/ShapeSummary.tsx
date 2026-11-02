// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useGeometrySubscription } from "@opencop/ingest";
import { Text } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import * as turf from "@turf/turf";
import type { Position2D } from "@opencop/elements";
import {
    Angle,
    BBOX_STEPS,
    CircleGeometry,
    EllipseGeometry,
    PolygonGeometry,
    PolylineGeometry,
} from "@opencop/elements";
import type { DetailsInfoSectionProps } from "@opencop/contributions";
import { DetailTable, type DetailTableRow, Section, useDisplayPreferences } from "@opencop/ui";
import {
    formatArea,
    formatDistance,
    summarizeCircle,
    summarizeLine,
    summarizePolygon,
} from "@opencop/utils";
import { formatAngleValue } from "./angle-format.util";

function valueText(text: string) {
    return (
        <Text color="bg.contrast" textStyle="xs">
            {text}
        </Text>
    );
}

/** An ellipse's circumference has no formula, so trace its boundary and measure that. */
function ellipsePerimeter(origin: Position2D, major: number, minor: number): number {
    // Perimeter doesn't depend on rotation, so the trace skips the bearing conversion.
    const boundary = turf.ellipse(origin, major, minor, { units: "meters", steps: BBOX_STEPS });
    return turf.length(turf.lineString(boundary.geometry.coordinates[0]), { units: "meters" });
}

/**
 * The shapes' Information-tab section: a shape's measurements (radius, length,
 * area, perimeter, the angle's segments). Renders nothing when it has none to
 * show. Registered for the shape kinds only, so it never reads a graphic's
 * control points as a line.
 */
export function ShapeSummary({ element }: DetailsInfoSectionProps) {
    const { t } = useTranslation();
    const { measurementSystem: system, angleUnit, angleOrientation } = useDisplayPreferences();
    useGeometrySubscription(element);

    const rows: DetailTableRow[] = [];
    const geo = element.geometry;

    // Dispatch on geometry class, not element.type, so rectangles (also a PolygonGeometry)
    // and future shape types work here without changes. Angle is checked by type since
    // it's a 3-point line that means something different from a plain line.
    if (geo instanceof CircleGeometry) {
        const radius = geo.getRadius();
        const [area, perimeter] = summarizeCircle(geo, system);
        rows.push(
            {
                key: "radius",
                label: t("events.fieldRadius"),
                value: valueText(formatDistance(radius, system).display),
            },
            { key: "perimeter", label: t("events.fieldPerimeter"), value: valueText(perimeter) },
            { key: "area", label: t("events.fieldArea"), value: valueText(area) },
        );
    }

    if (geo instanceof EllipseGeometry) {
        const major = geo.getMajor();
        const minor = geo.getMinor();
        if (major > 0 && minor > 0) {
            rows.push(
                {
                    key: "area",
                    label: t("events.fieldArea"),
                    value: valueText(formatArea(Math.PI * major * minor, system).display),
                },
                {
                    key: "perimeter",
                    label: t("events.fieldPerimeter"),
                    value: valueText(
                        formatDistance(
                            ellipsePerimeter(geo.getOrigin() as Position2D, major, minor),
                            system,
                        ).display,
                    ),
                },
            );
        }
    }

    if (geo instanceof PolygonGeometry) {
        const summary = summarizePolygon(geo, system);
        if (summary) {
            const [area, perimeter] = summary;
            rows.push(
                { key: "area", label: t("events.fieldArea"), value: valueText(area) },
                {
                    key: "perimeter",
                    label: t("events.fieldPerimeter"),
                    value: valueText(perimeter),
                },
            );
        }
    }

    if (geo instanceof PolylineGeometry) {
        if (Angle.is(element)) {
            // Mid-draw, an angle can have fewer than 3 points — same gate AngleInput
            // itself uses. Show nothing rather than a misleading line length.
            if (geo.numPositions >= 3) {
                const vertex = geo.getPos(1) as Position2D;
                const arm0 = geo.getPos(0) as Position2D;
                const arm1 = geo.getPos(2) as Position2D;
                const leg0Meters = turf.distance(vertex, arm0, { units: "meters" });
                const leg1Meters = turf.distance(vertex, arm1, { units: "meters" });
                const angleDegrees = Angle.getDirectionalAngle(element, angleOrientation);
                rows.push(
                    {
                        key: "segment1",
                        label: t("events.fieldSegment1"),
                        value: valueText(formatDistance(leg0Meters, system).display),
                    },
                    {
                        key: "segment2",
                        label: t("events.fieldSegment2"),
                        value: valueText(formatDistance(leg1Meters, system).display),
                    },
                    {
                        key: "angle",
                        label: t("events.fieldAngle"),
                        value: valueText(formatAngleValue(angleDegrees, angleUnit, t)),
                    },
                );
            }
        } else {
            const summary = summarizeLine(geo, system);
            if (summary) {
                rows.push({
                    key: "length",
                    label: t("events.fieldLength"),
                    value: valueText(summary[0]),
                });
            }
        }
    }

    if (rows.length === 0) return null;

    return (
        <Section heading={t("events.shapeDetails")}>
            <DetailTable rows={rows} />
        </Section>
    );
}
