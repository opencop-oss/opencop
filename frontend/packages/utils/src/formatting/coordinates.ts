// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { forward, toPoint } from "mgrs";

export type CoordFormat = "DD" | "DMS" | "DDM" | "MGRS" | "UTM";

export interface LatLng {
    lat: number;
    lng: number;
}

/** Format as decimal degrees: "38.897685, -77.036530" */
export function toDD(lat: number, lng: number): string {
    return `${lat.toFixed(6)}, ${lng.toFixed(6)}`;
}

/** Format as DMS: "38°53'51.67"N 77°02'11.51"W" */
export function toDMS(lat: number, lng: number): string {
    return `${decimalToDMS(lat, "lat")} ${decimalToDMS(lng, "lng")}`;
}

function decimalToDMS(decimal: number, axis: "lat" | "lng"): string {
    const absolute = Math.abs(decimal);
    const degrees = Math.floor(absolute);
    const minutesFloat = (absolute - degrees) * 60;
    const minutes = Math.floor(minutesFloat);
    const seconds = ((minutesFloat - minutes) * 60).toFixed(2);

    const direction = axis === "lat" ? (decimal >= 0 ? "N" : "S") : decimal >= 0 ? "E" : "W";

    return `${degrees}°${minutes.toString().padStart(2, "0")}'${seconds.padStart(5, "0")}"${direction}`;
}

/** Format as DDM: "38°53.862'N 77°02.190'W" */
export function toDDM(lat: number, lng: number): string {
    return `${decimalToDDM(lat, "lat")} ${decimalToDDM(lng, "lng")}`;
}

function decimalToDDM(decimal: number, axis: "lat" | "lng"): string {
    const absolute = Math.abs(decimal);
    const degrees = Math.floor(absolute);
    const minutes = ((absolute - degrees) * 60).toFixed(3);

    const direction = axis === "lat" ? (decimal >= 0 ? "N" : "S") : decimal >= 0 ? "E" : "W";

    return `${degrees}°${minutes.padStart(6, "0")}'${direction}`;
}

/** Format as MGRS: "18SUJ2337106519" */
export function toMGRS(lat: number, lng: number, precision = 5): string {
    try {
        return forward([lng, lat], precision);
    } catch {
        return "—";
    }
}

// --- WGS84 ellipsoid constants (shared by toUTM and parseUTM) ---
const WGS84_SEMI_MAJOR_AXIS = 6378137.0;
const WGS84_FLATTENING = 1 / 298.257223563;
const WGS84_SEMI_MINOR_AXIS = WGS84_SEMI_MAJOR_AXIS * (1 - WGS84_FLATTENING);
const WGS84_ECCENTRICITY_SQ =
    1 -
    (WGS84_SEMI_MINOR_AXIS * WGS84_SEMI_MINOR_AXIS) /
        (WGS84_SEMI_MAJOR_AXIS * WGS84_SEMI_MAJOR_AXIS);
const WGS84_SECOND_ECCENTRICITY_SQ = WGS84_ECCENTRICITY_SQ / (1 - WGS84_ECCENTRICITY_SQ);
const UTM_SCALE_FACTOR = 0.9996; // scale at the central meridian (UTM spec)
const UTM_FALSE_EASTING = 500000; // keeps all eastings positive within a zone
const UTM_FALSE_NORTHING = 10000000; // added in the southern hemisphere to keep northings positive

/**
 * Radius of curvature in the prime vertical — how curved the ellipsoid is east-west at a given latitude.
 * sinLat = Math.sin(latitudeInRadians)
 */
function primeVerticalRadius(sinLat: number): number {
    return WGS84_SEMI_MAJOR_AXIS / Math.sqrt(1 - WGS84_ECCENTRICITY_SQ * sinLat * sinLat);
}

/**
 * Radius of curvature in the meridian — how curved the ellipsoid is north-south at a given latitude.
 * sinLat = Math.sin(latitudeInRadians)
 */
function meridianRadius(sinLat: number): number {
    return (
        (WGS84_SEMI_MAJOR_AXIS * (1 - WGS84_ECCENTRICITY_SQ)) /
        (1 - WGS84_ECCENTRICITY_SQ * sinLat * sinLat) ** 1.5
    );
}

/** Which of the 60 UTM zones (1–60) a longitude falls in. */
function computeZone(lng: number): number {
    return Math.floor((lng + 180) / 6) + 1;
}

/** Central meridian of a zone in radians — the reference longitude for the Transverse Mercator projection. */
function computeCentralMeridianRad(zone: number): number {
    return ((zone - 1) * 6 - 180 + 3) * (Math.PI / 180);
}

/**
 * Meridional arc: the north-south distance along the ellipsoid from the equator to latRad.
 * Computed via a Fourier series accurate to sub-millimetre precision.
 */
function computeMeridionalArc(latRad: number): number {
    const e2 = WGS84_ECCENTRICITY_SQ;
    const e4 = e2 * e2;
    const e6 = e4 * e2;
    return (
        WGS84_SEMI_MAJOR_AXIS *
        ((1 - e2 / 4 - (3 * e4) / 64 - (5 * e6) / 256) * latRad -
            ((3 * e2) / 8 + (3 * e4) / 32 + (45 * e6) / 1024) * Math.sin(2 * latRad) +
            ((15 * e4) / 256 + (45 * e6) / 1024) * Math.sin(4 * latRad) -
            ((35 * e6) / 3072) * Math.sin(6 * latRad))
    );
}

/**
 * Easting in metres east of the zone's central meridian, offset by UTM_FALSE_EASTING.
 * lngOffsetRad is the longitude difference from the central meridian in radians.
 */
function projectEasting(latRad: number, lngOffsetRad: number): number {
    const sinLat = Math.sin(latRad);
    const cosLat = Math.cos(latRad);
    const tanLat = Math.tan(latRad);
    const N = primeVerticalRadius(sinLat);
    const tanSqLat = tanLat ** 2;
    const eccSqCosSqLat = WGS84_SECOND_ECCENTRICITY_SQ * cosLat ** 2;
    const A = cosLat * lngOffsetRad; // normalised longitude offset
    return (
        UTM_SCALE_FACTOR *
            N *
            (A +
                ((1 - tanSqLat + eccSqCosSqLat) * A ** 3) / 6 +
                ((5 -
                    18 * tanSqLat +
                    tanSqLat ** 2 +
                    72 * eccSqCosSqLat -
                    58 * WGS84_SECOND_ECCENTRICITY_SQ) *
                    A ** 5) /
                    120) +
        UTM_FALSE_EASTING
    );
}

/**
 * Northing in metres from the equator (before false northing for the southern hemisphere is added).
 * lngOffsetRad is the longitude difference from the central meridian in radians.
 */
function projectNorthing(latRad: number, lngOffsetRad: number): number {
    const sinLat = Math.sin(latRad);
    const cosLat = Math.cos(latRad);
    const tanLat = Math.tan(latRad);
    const N = primeVerticalRadius(sinLat);
    const tanSqLat = tanLat ** 2;
    const eccSqCosSqLat = WGS84_SECOND_ECCENTRICITY_SQ * cosLat ** 2;
    const A = cosLat * lngOffsetRad; // normalised longitude offset
    return (
        UTM_SCALE_FACTOR *
        (computeMeridionalArc(latRad) +
            N *
                tanLat *
                (A ** 2 / 2 +
                    ((5 - tanSqLat + 9 * eccSqCosSqLat + 4 * eccSqCosSqLat ** 2) * A ** 4) / 24 +
                    ((61 -
                        58 * tanSqLat +
                        tanSqLat ** 2 +
                        600 * eccSqCosSqLat -
                        330 * WGS84_SECOND_ECCENTRICITY_SQ) *
                        A ** 6) /
                        720))
    );
}

/**
 * Footpoint latitude: the latitude whose meridional arc equals the given distance.
 * Used as the starting point for the inverse UTM projection (parseUTM).
 */
function computeFootpointLatitude(meridionalArc: number): number {
    const e2 = WGS84_ECCENTRICITY_SQ;
    const e4 = e2 * e2;
    const e6 = e4 * e2;
    const rectifyingLatitude =
        meridionalArc / (WGS84_SEMI_MAJOR_AXIS * (1 - e2 / 4 - (3 * e4) / 64 - (5 * e6) / 256));
    const seriesCoeff = (1 - Math.sqrt(1 - e2)) / (1 + Math.sqrt(1 - e2));
    return (
        rectifyingLatitude +
        ((3 * seriesCoeff) / 2 - (27 * Math.pow(seriesCoeff, 3)) / 32) *
            Math.sin(2 * rectifyingLatitude) +
        ((21 * seriesCoeff * seriesCoeff) / 16 - (55 * Math.pow(seriesCoeff, 4)) / 32) *
            Math.sin(4 * rectifyingLatitude) +
        ((151 * Math.pow(seriesCoeff, 3)) / 96) * Math.sin(6 * rectifyingLatitude) +
        ((1097 * Math.pow(seriesCoeff, 4)) / 512) * Math.sin(8 * rectifyingLatitude)
    );
}

/** Inverse Transverse Mercator: recover latitude (radians) from footpoint latitude and normalised easting. */
function invertLat(footpointLatitude: number, normalizedEasting: number): number {
    const sinFp = Math.sin(footpointLatitude);
    const cosFp = Math.cos(footpointLatitude);
    const tanFp = Math.tan(footpointLatitude);
    const N = primeVerticalRadius(sinFp);
    const M = meridianRadius(sinFp);
    const tanSqFp = tanFp ** 2;
    const eccSqCosSqFp = WGS84_SECOND_ECCENTRICITY_SQ * cosFp ** 2;
    const D = normalizedEasting;
    return (
        footpointLatitude -
        ((N * tanFp) / M) *
            (D ** 2 / 2 -
                ((5 +
                    3 * tanSqFp +
                    10 * eccSqCosSqFp -
                    4 * eccSqCosSqFp ** 2 -
                    9 * WGS84_SECOND_ECCENTRICITY_SQ) *
                    D ** 4) /
                    24 +
                ((61 +
                    90 * tanSqFp +
                    298 * eccSqCosSqFp +
                    45 * tanSqFp ** 2 -
                    252 * WGS84_SECOND_ECCENTRICITY_SQ -
                    3 * eccSqCosSqFp ** 2) *
                    D ** 6) /
                    720)
    );
}

/**
 * Returns the UTM latitude band letter for a given latitude.
 * Bands run C–X (8° each, skipping I and O) from 80°S to 84°N.
 * Band X is extended to 12° to cover 72°N–84°N.
 */
function utmLatBand(lat: number): string {
    const bands = "CDEFGHJKLMNPQRSTUVWX";
    if (lat >= 72) return "X";
    const index = Math.floor((lat + 80) / 8);
    return bands[Math.min(Math.max(index, 0), 19)];
}

/** Inverse Transverse Mercator: recover longitude (radians) from central meridian, footpoint latitude, and normalised easting. */
function invertLng(
    centralMeridianRad: number,
    footpointLatitude: number,
    normalizedEasting: number,
): number {
    const cosFp = Math.cos(footpointLatitude);
    const tanFp = Math.tan(footpointLatitude);
    const tanSqFp = tanFp ** 2;
    const eccSqCosSqFp = WGS84_SECOND_ECCENTRICITY_SQ * cosFp ** 2;
    const D = normalizedEasting;
    return (
        centralMeridianRad +
        (D -
            ((1 + 2 * tanSqFp + eccSqCosSqFp) * D ** 3) / 6 +
            ((5 -
                2 * eccSqCosSqFp +
                28 * tanSqFp -
                3 * eccSqCosSqFp ** 2 +
                8 * WGS84_SECOND_ECCENTRICITY_SQ +
                24 * tanSqFp ** 2) *
                D ** 5) /
                120) /
            cosFp
    );
}

/**
 * Converts latitude/longitude (WGS84) into UTM (Universal Transverse Mercator).
 *
 * --- Mental model ---
 * Think of UTM like placing a transparent grid over the Earth.
 *
 * - Longitude determines which "grid zone" you're on (which sheet of paper)
 * - Latitude + longitude are then converted into meters on that sheet
 *
 * So:
 *   Easting  = how far left/right you are on the sheet (metres from the zone's centre line)
 *   Northing = how far up/down you are on the sheet (metres from the equator)
 *
 * --- What UTM is (simple version) ---
 * UTM is a flat grid system that divides the Earth into 60 zones.
 * Each zone is 6° of longitude wide and uses meters instead of degrees.
 *
 * This makes it easier to:
 * - measure distances directly in meters
 * - draw accurate local maps
 * - avoid distortion compared to lat/lng over small areas
 *
 * --- Output format ---
 * Example: "18T 585628E 4511278N"
 *
 * Breakdown:
 *   18T        → UTM zone (18) + latitude band (T)
 *   585628E    → Easting (meters east within zone)
 *   4511278N   → Northing (meters north from equator)
 *
 * --- Valid range ---
 * UTM is only defined between:
 *   80°S and 84°N
 * Polar regions use UPS instead.
 *
 * --- Notes ---
 * - Uses WGS84 datum (same as GPS)
 * - Results are approximate at sub-meter precision
 * - Zones are purely longitude-based (lat is NOT used for zone selection)
 */
export function toUTM(lat: number, lng: number): string {
    // UTM is only defined between 80°S and 84°N; polar regions use a different system (UPS).
    if (lat > 84 || lat < -80) return "—";

    // Normalize longitude to [-180, 180) so that 180° maps to zone 1 (same as -180°)
    // rather than the non-existent zone 61. The antimeridian is reachable on the map.
    const normLng = ((((lng + 180) % 360) + 360) % 360) - 180;
    const zone = computeZone(normLng);
    const centralMeridianRad = computeCentralMeridianRad(zone);
    const latRad = lat * (Math.PI / 180);
    const lngOffsetRad = normLng * (Math.PI / 180) - centralMeridianRad;

    const easting = projectEasting(latRad, lngOffsetRad);
    const northingFromEquator = projectNorthing(latRad, lngOffsetRad);
    const falseNorthing = lat < 0 ? UTM_FALSE_NORTHING : 0;
    const band = utmLatBand(lat);

    return `${zone}${band} ${Math.round(easting)}E ${Math.round(northingFromEquator + falseNorthing)}N`;
}

/** Format a coordinate in the requested format. */
export function formatCoord(lat: number, lng: number, format: CoordFormat): string {
    switch (format) {
        case "DD":
            return toDD(lat, lng);
        case "DMS":
            return toDMS(lat, lng);
        case "DDM":
            return toDDM(lat, lng);
        case "MGRS":
            return toMGRS(lat, lng);
        case "UTM":
            return toUTM(lat, lng);
    }
}

/**
 * Parse a user-entered coordinate string in any supported format.
 * Returns { lat, lng } or null if unparseable.
 *
 * Supported inputs:
 *   DD:   "38.897685, -77.036530"  or  "38.897685 -77.036530"
 *   DMS:  "38°53'51.67"N 77°02'11.51"W"  (various separator styles)
 *   DDM:  "38°53.862'N 77°02.190'W"
 *   MGRS: "18SUJ2337106519"
 *   UTM:  "18T 585628E 4511278N"  or  "18T 585628 4511278"
 */
function normalizeCoordinateInput(input: string): string {
    return input.replace(/[′‘’']/g, "'").replace(/[″“”"]/g, '"');
}

export function parseCoordinate(input: string): LatLng | null {
    const trimmed = normalizeCoordinateInput(input.trim());
    if (!trimmed) return null;

    return (
        parseMGRS(trimmed) ??
        parseUTM(trimmed) ??
        parseDMS(trimmed) ??
        parseCondensedDMS(trimmed) ??
        parseDDM(trimmed) ??
        parseDD(trimmed) ??
        null
    );
}

function parseDD(input: string): LatLng | null {
    // "38.897685, -77.036530" or "38.897685 -77.036530"
    const match = input.match(/^([+-]?\d+\.?\d*)[,\s]+([+-]?\d+\.?\d*)$/);
    if (!match) return null;

    const a = parseFloat(match[1]);
    const b = parseFloat(match[2]);

    if (isNaN(a) || isNaN(b)) return null;

    // Try lat,lng order first
    if (Math.abs(a) <= 90 && Math.abs(b) <= 180) {
        return { lat: a, lng: b };
    }
    // Fall back to lng,lat order (GeoJSON convention) if first value > 90
    if (Math.abs(a) <= 180 && Math.abs(b) <= 90) {
        return { lat: b, lng: a };
    }

    return null;
}

function parseDMS(input: string): LatLng | null {
    // Match patterns like: 38°53'51.67"N  or  38 53 51.67 N
    const dmsRegex =
        /(\d+)[°\s]+(\d+)['\s]+(\d+\.?\d*)["\s]*([NSns])\s*[,\s]*(\d+)[°\s]+(\d+)['\s]+(\d+\.?\d*)["\s]*([EWew])/;
    const match = input.match(dmsRegex);
    if (!match) return null;

    const latDeg = parseInt(match[1]);
    const latMin = parseInt(match[2]);
    const latSec = parseFloat(match[3]);
    const latDir = match[4].toUpperCase();

    const lngDeg = parseInt(match[5]);
    const lngMin = parseInt(match[6]);
    const lngSec = parseFloat(match[7]);
    const lngDir = match[8].toUpperCase();

    if (latMin >= 60 || latSec >= 60 || lngMin >= 60 || lngSec >= 60) return null;

    let lat = latDeg + latMin / 60 + latSec / 3600;
    let lng = lngDeg + lngMin / 60 + lngSec / 3600;

    if (latDir === "S") lat = -lat;
    if (lngDir === "W") lng = -lng;

    if (Math.abs(lat) > 90 || Math.abs(lng) > 180) return null;

    return { lat, lng };
}

// Parses condensed/military DMS: 250302N 1213107E or 250302N1213107E
// Latitude is always 6 digits (DDMMSS), longitude always 7 (DDDMMSS).
// Slash-separated variants like "250302N / 1213107E" are intentionally excluded:
// a bare slash appears in UTM strings, file paths, and other numeric contexts,
// so matching it here would produce false positives against those inputs.
function parseCondensedDMS(input: string): LatLng | null {
    const match = input.match(/^(\d{2})(\d{2})(\d{2})([NSns])\s*(\d{3})(\d{2})(\d{2})([EWew])$/);
    if (!match) return null;

    const latDeg = parseInt(match[1]);
    const latMin = parseInt(match[2]);
    const latSec = parseInt(match[3]);
    const latDir = match[4].toUpperCase();

    const lngDeg = parseInt(match[5]);
    const lngMin = parseInt(match[6]);
    const lngSec = parseInt(match[7]);
    const lngDir = match[8].toUpperCase();

    if (latMin >= 60 || latSec >= 60 || lngMin >= 60 || lngSec >= 60) return null;

    let lat = latDeg + latMin / 60 + latSec / 3600;
    let lng = lngDeg + lngMin / 60 + lngSec / 3600;

    if (latDir === "S") lat = -lat;
    if (lngDir === "W") lng = -lng;

    if (Math.abs(lat) > 90 || Math.abs(lng) > 180) return null;

    return { lat, lng };
}

function parseDDM(input: string): LatLng | null {
    // Match patterns like: 38°53.862'N 77°02.190'W
    const ddmRegex =
        /(\d+)[°\s]+(\d+\.?\d*)['\s]*([NSns])\s*[,\s]*(\d+)[°\s]+(\d+\.?\d*)['\s]*([EWew])/;
    const match = input.match(ddmRegex);
    if (!match) return null;

    const latDeg = parseInt(match[1]);
    const latMin = parseFloat(match[2]);
    const latDir = match[3].toUpperCase();

    const lngDeg = parseInt(match[4]);
    const lngMin = parseFloat(match[5]);
    const lngDir = match[6].toUpperCase();

    if (latMin >= 60 || lngMin >= 60) return null;

    let lat = latDeg + latMin / 60;
    let lng = lngDeg + lngMin / 60;

    if (latDir === "S") lat = -lat;
    if (lngDir === "W") lng = -lng;

    if (Math.abs(lat) > 90 || Math.abs(lng) > 180) return null;

    return { lat, lng };
}

function parseMGRS(input: string): LatLng | null {
    // Valid MGRS: 1-2 digit zone number, 1 latitude band [C-X excluding I/O],
    // 2 column/row letters [A-HJ-NP-Z], then an even number of easting/northing digits (2-10)
    const cleaned = input.replace(/\s/g, "");
    if (!/^\d{1,2}[C-HJ-NP-X][A-HJ-NP-Z]{2}\d{2,10}$/i.test(cleaned)) return null;

    // Verify even number of location digits
    const locDigits = cleaned.replace(/^\d{1,2}[A-Za-z]{3}/, "");
    if (locDigits.length % 2 !== 0) return null;

    try {
        const point = toPoint(cleaned);
        const lng = point[0];
        const lat = point[1];
        if (isNaN(lat) || isNaN(lng)) return null;
        return { lat, lng };
    } catch {
        return null;
    }
}
/**
 * Parses a UTM (Universal Transverse Mercator) coordinate string into latitude/longitude.
 *
 * --- What is UTM? ---
 * UTM is a global coordinate system that divides the Earth into 60 vertical "zones".
 * Each zone is 6° wide and uses a flat grid measured in meters.
 *
 * A UTM coordinate looks like:
 *   "18T 585628E 4511278N"
 *
 * It has 3 main parts:
 *   1. Zone (18T)
 *      - 18 = longitudinal zone (1–60)
 *      - T  = latitude band (C–X, excludes I and O)
 *
 *   2. Easting (585628)
 *      - Distance in meters EAST from the zone’s central meridian
 *      - Always around 100,000–900,000
 *
 *   3. Northing (4511278)
 *      - Distance in meters NORTH from the equator
 *      - In southern hemisphere, a "false northing" of 10,000,000 is added
 *
 * --- Supported formats ---
 *   "18T 585628E 4511278N"
 *   "18T 585628 4511278"
 *   "18T585628E4511278N"
 *
 * Returns:
 *   { lat, lng } in WGS84 (standard GPS lat/lng), or null if invalid
 */
function parseUTM(input: string): LatLng | null {
    // Accepts: "18T 585628E 4511278N", "18T 585628 4511278", "18T585628E4511278N"
    // Match: zone number, band letter, easting, northing
    // Spaces between parts are optional; trailing E/N suffixes are optional.
    const match = input
        .trim()
        .match(/^(\d{1,2})\s*([C-HJ-NP-X])\s*(\d+)\s*[Ee]?\s*(\d+)\s*[Nn]?$/i);
    if (!match) return null;

    const zone = parseInt(match[1]);
    // Note: latitude band is only used to determine hemisphere,
    // not to strictly clamp latitude range.
    const band = match[2].toUpperCase();
    const easting = parseFloat(match[3]);
    const northing = parseFloat(match[4]);
    if (zone < 1 || zone > 60) return null; // zones 1–60 are the only valid UTM zones

    // Soft bounds — not strictly defined by the UTM spec, but catch obvious garbage early.
    // The lat/lng range check at the end of this function is the authoritative safety net.
    if (easting < 100000 || easting > 900000) return null;
    if (northing < 0 || northing > 10000000) return null;

    // --- Hemisphere handling ---
    // Bands C–M are southern hemisphere, N–X are northern
    // Southern hemisphere northings have a 10,000,000 m false northing applied; remove it
    // to get the true distance from the equator (which will be negative in the south).
    const southBands = "CDEFGHJKLM";
    const isNorthernHemisphere = !southBands.includes(band);
    // Southern hemisphere uses a "false northing" of 10,000,000m
    // Remove it so math works relative to the equator
    const northingFromEquator = isNorthernHemisphere ? northing : northing - 10000000;

    const centralMeridianRad = computeCentralMeridianRad(zone);

    // Undo the scale factor to get the raw meridional arc distance from the equator.
    const meridionalArc = northingFromEquator / UTM_SCALE_FACTOR;

    // Find the footpoint latitude — the latitude whose meridional arc matches our northing.
    // This is the anchor for the inverse projection series below.
    const footpointLatitude = computeFootpointLatitude(meridionalArc);

    // Normalise the easting so the inverse series works in a dimensionless coordinate.
    const normalizedEasting =
        (easting - UTM_FALSE_EASTING) /
        (primeVerticalRadius(Math.sin(footpointLatitude)) * UTM_SCALE_FACTOR);

    // Recover lat/lng using the inverse Transverse Mercator series.
    const latRad = invertLat(footpointLatitude, normalizedEasting);
    const lngRad = invertLng(centralMeridianRad, footpointLatitude, normalizedEasting);

    const latDeg = latRad * (180 / Math.PI);
    const lngDeg = lngRad * (180 / Math.PI);

    if (isNaN(latDeg) || isNaN(lngDeg)) return null;
    if (latDeg < -90 || latDeg > 90 || lngDeg < -180 || lngDeg > 180) return null;

    return { lat: latDeg, lng: lngDeg };
}
