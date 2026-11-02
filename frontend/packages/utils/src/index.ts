// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

export {
    fromMeters,
    toMeters,
    type DistanceUnit,
    type MeasurementSystem,
} from "./units/measurement-system";
export { MILS_PER_DEGREE, type AngleOrientation, type AngleUnit } from "./units/angle";
export {
    breakdownDistance,
    combineDistance,
    formatArea,
    formatDistance,
    type DistanceBreakdown,
    type FormattedDistance,
} from "./formatting/distance";
export {
    formatCoord,
    parseCoordinate,
    type CoordFormat,
    type LatLng,
} from "./formatting/coordinates";
export {
    summarizeCircle,
    summarizeLine,
    summarizePolygon,
    type AreaGeometry,
    type PathGeometry,
    type RadiusGeometry,
} from "./formatting/summary";
export { mercatorMidpoint } from "./geometry/mercator";
export { directionalAngle } from "./geometry/angle";
export { hexToRgba } from "./color/hex";
