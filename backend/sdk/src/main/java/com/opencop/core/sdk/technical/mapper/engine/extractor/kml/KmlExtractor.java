// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.extractor.kml;

import com.opencop.core.sdk.model.mapper.profile.DynamicMapper;
import com.opencop.core.sdk.model.mapper.rules.MappingRule;
import com.opencop.core.sdk.model.mapper.source.XPathSource;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import com.opencop.core.sdk.technical.mapper.engine.extractor.PartAwareExtractor;
import com.opencop.core.sdk.technical.mapper.engine.extractor.RecordExtractor;
import com.opencop.core.sdk.technical.mapper.engine.extractor.xml.XmlExtractor;
import com.opencop.core.sdk.technical.model.kml.KmlStyle;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;
import org.apache.commons.lang3.StringUtils;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.geojson.GeoJsonWriter;
import org.locationtech.jts.io.kml.KMLReader;

public class KmlExtractor implements RecordExtractor<XdmNode>, PartAwareExtractor<XdmNode> {

  private static final String KML_NS = "http://www.opengis.net/kml/2.2";
  private static final String GX_NS = "http://www.google.com/kml/ext/2.2";
  private static final String XLINK_NS = "http://www.w3.org/1999/xlink";
  private static final Pattern COORDINATES_ELEMENT =
      Pattern.compile("(<(?:\\w+:)?coordinates>)(.*?)(</(?:\\w+:)?coordinates>)", Pattern.DOTALL);
  private static final Pattern COORDINATE_COMMA_PADDING = Pattern.compile("\\s*,\\s*");

  private final XmlExtractor delegate;

  private final AtomicReference<ExtractionState> extractionState =
      new AtomicReference<>(ExtractionState.empty());

  private final KMLReader kmlReader = new KMLReader();
  private final GeoJsonWriter geoJsonWriter = new GeoJsonWriter();

  public KmlExtractor(XmlExtractor delegate) {
    this.delegate = delegate;
  }

  public static KmlExtractor fromProfile(DynamicMapper profile) {
    return new KmlExtractor(XmlExtractor.fromProfile(profile));
  }

  @Override
  public int partCount(XdmNode node) {
    ExtractionState state = extractionState.get();
    Integer idx = state.indexOf(node);
    if (idx == null) {
      return 1;
    }

    List<Part> parts = state.partsAt(idx);
    if (parts == null || parts.isEmpty()) {
      return 1;
    }

    return parts.size();
  }

  @Override
  public String selectString(XdmNode node, int partIndex, MappingRule rule)
      throws MapperEngineException {
    String expr = ((XPathSource) rule.source()).expr();
    ExtractionState state = extractionState.get();

    Integer idx = state.indexOf(node);
    if (idx == null) return delegate.selectString(node, rule);

    List<Part> parts = state.partsAt(idx);
    int p = clampIndex(partIndex, parts == null ? 0 : parts.size());

    if ("kmlgeom:count".equals(expr)) {
      return parts == null ? "0" : Integer.toString(parts.size());
    }

    if ("kmlgeom:geojson".equals(expr)) {
      return parts == null ? null : parts.get(p).geoJson();
    }

    if (expr != null && expr.startsWith("kmlstyle:")) {
      String key = expr.substring("kmlstyle:".length());
      KmlStyle style = (parts == null) ? null : parts.get(p).style();
      if (style == null) return null;

      return switch (key) {
        case "strokeColor" -> style.strokeColor();
        case "strokeWidth" -> style.strokeWidth() == null ? null : style.strokeWidth().toString();
        case "fillColor" -> style.fillColor();
        case "iconHref" -> style.iconHref();
        default -> null;
      };
    }

    return delegate.selectString(node, rule);
  }

  private static int clampIndex(int index, int size) {
    if (size <= 0) {
      return 0;
    }
    if (index < 0) {
      return 0;
    }
    if (index >= size) {
      return size - 1;
    }
    return index;
  }

  private record Part(String geoJson, KmlStyle style) {}

  private record ExtractionState(
      IdentityHashMap<XdmNode, Integer> indexByNode, List<Part>[] partsByIndex) {
    private static final ExtractionState EMPTY =
        new ExtractionState(new IdentityHashMap<>(), emptyParts());

    static ExtractionState empty() {
      return EMPTY;
    }

    Integer indexOf(XdmNode node) {
      return indexByNode.get(node);
    }

    List<Part> partsAt(Integer idx) {
      if (idx == null || idx < 0 || idx >= partsByIndex.length) {
        return null;
      }
      return partsByIndex[idx];
    }

    @SuppressWarnings("unchecked")
    private static List<Part>[] emptyParts() {
      return new List[0];
    }
  }

  @Override
  public List<XdmNode> selectRecords(byte[] payload, DynamicMapper profile)
      throws MapperEngineException {
    List<XdmNode> records = delegate.selectRecords(payload, profile);
    if (records.isEmpty()) {
      extractionState.set(ExtractionState.empty());
      return records;
    }

    IdentityHashMap<XdmNode, Integer> indexByNode = new IdentityHashMap<>(records.size());
    for (int i = 0; i < records.size(); i++) {
      indexByNode.put(records.get(i), i);
    }

    Processor pro = records.getFirst().getProcessor();

    XPathCompiler comp = pro.newXPathCompiler();
    comp.declareNamespace("kml", KML_NS);
    comp.declareNamespace("gx", GX_NS);
    comp.declareNamespace("xlink", XLINK_NS);

    XdmNode doc = parseWith(pro, payload);
    KmlStyleIndex styleIndex = KmlStyleIndex.from(doc, comp);

    @SuppressWarnings("unchecked")
    List<Part>[] partsByIndex = new List[records.size()];

    for (int i = 0; i < records.size(); i++) {
      XdmNode placemark = records.get(i);

      KmlStyle placemarkStyle = styleIndex.resolveForPlacemark(placemark);
      partsByIndex[i] = resolvePlacemarkParts(placemark, placemarkStyle);
    }
    extractionState.set(new ExtractionState(indexByNode, partsByIndex));

    return records;
  }

  private XdmNode parseWith(Processor pro, byte[] xmlBytes) throws MapperEngineException {
    try {
      return pro.newDocumentBuilder().build(new StreamSource(new ByteArrayInputStream(xmlBytes)));
    } catch (Exception e) {
      throw new MapperEngineException("XML_PARSING_ERROR", "Failed to parse KML XML", e);
    }
  }

  private List<Part> resolvePlacemarkParts(XdmNode placemark, KmlStyle placemarkStyle)
      throws MapperEngineException {
    try {
      List<XdmNode> top = selectGeometryNodes(placemark);
      if (top.isEmpty()) {
        return List.of();
      }

      List<Part> out = new ArrayList<>();

      for (XdmNode geo : top) {
        for (XdmNode leaf : flattenMultiGeometry(geo)) {
          String geoJson = geometryNodeToGeoJson(leaf);
          if (geoJson != null) {
            out.add(new Part(geoJson, placemarkStyle));
          }
        }
      }

      return out;
    } catch (Exception e) {
      throw new MapperEngineException(
          "KML_GEOMETRY_ERROR", "Failed to extract KML geometry parts", e);
    }
  }

  private String geometryNodeToGeoJson(XdmNode geomNode) throws ParseException {
    String geomXml = new String(delegate.toBytes(geomNode), StandardCharsets.UTF_8);
    Geometry geom = kmlReader.read(normalizeCoordinatesForJts(geomXml));
    if (geom == null || geom.isEmpty()) {
      return null;
    }
    return geoJsonWriter.write(geom);
  }

  private static String normalizeCoordinatesForJts(String geomXml) {
    Matcher matcher = COORDINATES_ELEMENT.matcher(geomXml);
    if (!matcher.find()) {
      return geomXml;
    }

    StringBuffer out = new StringBuffer();
    do {
      // Real-world KML often emits coordinate tuples as "lon, lat, alt". JTS KMLReader rejects
      // padding around commas, so normalize only the tuple separators and keep tuple whitespace.
      String normalizedCoordinates =
          COORDINATE_COMMA_PADDING.matcher(matcher.group(2)).replaceAll(",");
      matcher.appendReplacement(
          out,
          Matcher.quoteReplacement(
              matcher.group(1) + normalizedCoordinates.trim() + matcher.group(3)));
    } while (matcher.find());
    matcher.appendTail(out);
    return out.toString();
  }

  @Override
  public String selectString(XdmNode context, MappingRule rule) throws MapperEngineException {
    if (!(rule.source() instanceof XPathSource)) {
      throw new MapperEngineException("BAD_RULE_SOURCE", "Expected an XML rule source", null);
    }
    String expr = ((XPathSource) rule.source()).expr();
    if (StringUtils.isEmpty(expr)) {
      return null;
    }

    ExtractionState state = extractionState.get();
    Integer idx = indexOf(state, context);
    if (idx == null) {
      return delegate.selectString(context, rule);
    }

    List<Part> parts = partsAt(state, idx);

    if ("kmlgeom:count".equals(expr)) {
      return geometryCount(parts);
    }
    if (expr.startsWith("kmlgeom:geojson")) {
      return geoJsonAt(expr, parts);
    }
    if (expr.startsWith("kmlstyle:")) {
      return styleValueAt(expr, parts);
    }

    return delegate.selectString(context, rule);
  }

  private Integer indexOf(ExtractionState state, XdmNode context) {
    return state.indexOf(context);
  }

  private List<Part> partsAt(ExtractionState state, int idx) {
    return state.partsAt(idx);
  }

  private String geometryCount(List<Part> parts) {
    return parts == null ? "0" : Integer.toString(parts.size());
  }

  private String geoJsonAt(String expr, List<Part> parts) {
    int partIndex = parseOptionalIndex(expr);
    Part part = partAt(parts, partIndex);
    return part == null ? null : part.geoJson();
  }

  private String styleValueAt(String expr, List<Part> parts) {
    StyleLookup lookup = parseStyleLookup(expr);
    Part part = partAt(parts, lookup.partIndex());
    if (part == null || part.style() == null) return null;

    KmlStyle style = part.style();
    return switch (lookup.key()) {
      case "strokeColor" -> style.strokeColor();
      case "strokeWidth" -> style.strokeWidth() == null ? null : style.strokeWidth().toString();
      case "fillColor" -> style.fillColor();
      case "iconHref" -> style.iconHref();
      default -> null;
    };
  }

  private Part partAt(List<Part> parts, int partIndex) {
    if (parts == null || partIndex < 0 || partIndex >= parts.size()) return null;
    return parts.get(partIndex);
  }

  private StyleLookup parseStyleLookup(String expr) {
    String rest = expr.substring("kmlstyle:".length());
    int dot = rest.lastIndexOf('.');
    if (dot <= 0) return new StyleLookup(rest, 0);

    String maybeIndex = rest.substring(dot + 1);
    if (!maybeIndex.matches("\\d+")) return new StyleLookup(rest, 0);

    String key = rest.substring(0, dot);
    return new StyleLookup(key, Integer.parseInt(maybeIndex));
  }

  private record StyleLookup(String key, int partIndex) {}

  private static int parseOptionalIndex(String expr) {
    if (expr.length() == "kmlgeom:geojson".length()) {
      return 0;
    }
    if (expr.startsWith("kmlgeom:geojson" + ".")) {
      String n = expr.substring("kmlgeom:geojson".length() + 1);
      if (n.matches("\\d+")) {
        return Integer.parseInt(n);
      }
    }
    return 0;
  }

  @Override
  public byte[] toBytes(XdmNode recordEntry) {
    return delegate.toBytes(recordEntry);
  }

  private List<XdmNode> selectGeometryNodes(XdmNode placemark) throws SaxonApiException {
    XPathCompiler comp = compilerFor(placemark);
    XPathSelector selector =
        comp.compile("./kml:Point | ./kml:LineString | ./kml:Polygon | ./kml:MultiGeometry").load();
    selector.setContextItem(placemark);

    XdmValue value = selector.evaluate();
    if (value == null || value.isEmptySequence()) return List.of();

    List<XdmNode> out = new java.util.ArrayList<>();
    for (int i = 0; i < value.size(); i++) {
      XdmItem item = value.itemAt(i);
      if (item instanceof XdmNode n) out.add(n);
    }
    return out;
  }

  private List<XdmNode> flattenMultiGeometry(XdmNode node) throws SaxonApiException {
    // If it's not MultiGeometry, return itself
    if (!"MultiGeometry".equals(node.getNodeName().getLocalName())) {
      return List.of(node);
    }
    XPathCompiler comp = compilerFor(node);
    XPathSelector selector = comp.compile("./kml:Point | ./kml:LineString | ./kml:Polygon").load();
    selector.setContextItem(node);

    XdmValue value = selector.evaluate();
    if (value == null || value.isEmptySequence()) {
      return List.of();
    }

    List<XdmNode> out = new ArrayList<>();
    for (int i = 0; i < value.size(); i++) {
      XdmItem item = value.itemAt(i);
      if (item instanceof XdmNode n) {
        out.add(n);
      }
    }
    return out;
  }

  private XPathCompiler compilerFor(XdmNode node) {
    Processor pro = node.getProcessor();
    XPathCompiler comp = pro.newXPathCompiler();
    comp.declareNamespace("kml", KML_NS);
    comp.declareNamespace("gx", GX_NS);
    return comp;
  }
}
