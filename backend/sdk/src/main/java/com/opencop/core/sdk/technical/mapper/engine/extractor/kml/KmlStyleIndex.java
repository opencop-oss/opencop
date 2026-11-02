// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.extractor.kml;

import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import com.opencop.core.sdk.technical.model.kml.KmlStyle;
import java.util.HashMap;
import java.util.Map;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;

final class KmlStyleIndex {

  private final Map<String, KmlStyle> styleById;
  private final Map<String, String> styleMapNormalStyleId; // styleMapId -> styleId

  private KmlStyleIndex(
      Map<String, KmlStyle> styleById, Map<String, String> styleMapNormalStyleId) {
    this.styleById = styleById;
    this.styleMapNormalStyleId = styleMapNormalStyleId;
  }

  static KmlStyleIndex from(XdmNode doc, XPathCompiler compiler) throws MapperEngineException {
    try {
      Map<String, KmlStyle> styles = new HashMap<>();
      Map<String, String> styleMaps = new HashMap<>();

      // Styles
      XdmValue styleNodes = eval(doc, compiler, "//kml:Style[@id]");
      for (XdmItem it : styleNodes) {
        XdmNode n = (XdmNode) it;
        String id = n.getAttributeValue(new QName("id"));
        if (id == null || id.isBlank()) continue;
        styles.put(id, parseStyleNode(n, compiler));
      }

      // StyleMaps (take "normal")
      XdmValue styleMapNodes = eval(doc, compiler, "//kml:StyleMap[@id]");
      for (XdmItem it : styleMapNodes) {
        XdmNode sm = (XdmNode) it;
        String id = sm.getAttributeValue(new QName("id"));
        if (id == null || id.isBlank()) continue;

        String normalUrl = string(sm, compiler, "kml:Pair[kml:key='normal']/kml:styleUrl");
        String normalId = normalizeStyleId(normalUrl);
        if (normalId != null) styleMaps.put(id, normalId);
      }

      return new KmlStyleIndex(styles, styleMaps);
    } catch (Exception e) {
      throw new MapperEngineException("KML_STYLE_ERROR", "Failed to build KML style index", e);
    }
  }

  KmlStyle resolveForPlacemark(XdmNode placemark) throws MapperEngineException {
    try {
      XPathCompiler c = placemark.getProcessor().newXPathCompiler();
      c.declareNamespace("kml", "http://www.opengis.net/kml/2.2");

      // Inline style
      XdmNode inline = node(placemark, c);
      if (inline != null) return parseStyleNode(inline, c);

      // styleUrl reference
      String styleUrl = string(placemark, c, "kml:styleUrl");
      String id = normalizeStyleId(styleUrl);
      if (id == null) return null;

      // styleUrl might refer to StyleMap id
      String mapped = styleMapNormalStyleId.get(id);
      if (mapped != null) id = mapped;

      return styleById.get(id);
    } catch (Exception e) {
      throw new MapperEngineException("KML_STYLE_ERROR", "Failed to resolve placemark style", e);
    }
  }

  private static KmlStyle parseStyleNode(XdmNode style, XPathCompiler c) throws SaxonApiException {
    // LineStyle
    String lineColorKml = string(style, c, "kml:LineStyle/kml:color");
    Double width = parseDouble(string(style, c, "kml:LineStyle/kml:width"));
    Double strokeOpacity = parseDouble(string(style, c, "kml:LineStyle/kml:opacity"));

    // PolyStyle
    String polyColorKml = string(style, c, "kml:PolyStyle/kml:color");
    Double fillOpacity = parseDouble(string(style, c, "kml:PolyStyle/kml:opacity"));

    String stroke = buildHex8FromKmlColorAndOpacity(lineColorKml, strokeOpacity);
    String fill = buildHex8FromKmlColorAndOpacity(polyColorKml, fillOpacity);

    String iconHref =
        firstNonBlank(
            style,
            c,
            "kml:IconStyle/kml:Icon/kml:href/text()",
            "kml:IconStyle/kml:href/text()",
            "kml:Icon/kml:href/text()",
            "kml:IconStyle/kml:Icon/@xlink:href",
            "kml:Icon/@xlink:href");

    return new KmlStyle(stroke, width, fill, iconHref);
  }

  private record ParsedColor(String rgb6, Double alphaFromColor) {}

  private static String buildHex8FromKmlColorAndOpacity(String kmlColor, Double opacityOverride) {
    ParsedColor parsedColor = parseKmlColorToRgbAndAlpha(kmlColor);
    if (parsedColor.rgb6() == null) {
      return null;
    }

    double alpha;

    if (opacityOverride != null) {
      alpha = Math.clamp(opacityOverride, 0.0, 1.0);
    } else if (parsedColor.alphaFromColor() != null) {
      alpha = Math.clamp(parsedColor.alphaFromColor(), 0.0, 1.0);
    } else {
      alpha = 1.0;
    }

    String aa = String.format("%02X", (int) Math.round(alpha * 255.0));
    return ("#" + parsedColor.rgb6() + aa).toUpperCase(java.util.Locale.ROOT);
  }

  private static ParsedColor parseKmlColorToRgbAndAlpha(String kml) {
    if (kml == null) {
      return new ParsedColor(null, null);
    }

    String color = kml.trim();
    if (color.startsWith("#")) {
      color = color.substring(1);
    }
    color = color.toLowerCase(java.util.Locale.ROOT);

    if (color.matches("[0-9a-f]{8}")) {
      // aabbggrr
      String aa = color.substring(0, 2);
      String bb = color.substring(2, 4);
      String gg = color.substring(4, 6);
      String rr = color.substring(6, 8);

      String rgb = (rr + gg + bb).toUpperCase(java.util.Locale.ROOT);
      double alpha = Integer.parseInt(aa, 16) / 255.0;
      return new ParsedColor(rgb, alpha);
    }

    if (color.matches("[0-9a-f]{6}")) {
      // rrggbb
      return new ParsedColor(color.toUpperCase(java.util.Locale.ROOT), null);
    }

    return new ParsedColor(null, null);
  }

  private static String normalizeStyleId(String styleUrl) {
    if (styleUrl == null) {
      return null;
    }
    String s = styleUrl.trim();
    if (s.startsWith("#")) {
      return s.substring(1);
    }
    return null; // external style URLs not supported in this pass
  }

  private static Double parseDouble(String s) {
    if (s == null || s.isBlank()) {
      return null;
    }
    try {
      return Double.parseDouble(s.trim());
    } catch (Exception e) {
      return null;
    }
  }

  private static XdmValue eval(XdmNode ctx, XPathCompiler c, String expr) throws SaxonApiException {
    XPathSelector selector = c.compile(expr).load();
    selector.setContextItem(ctx);
    return selector.evaluate();
  }

  private static String string(XdmNode ctx, XPathCompiler compiler, String expr)
      throws SaxonApiException {
    XPathSelector selector = compiler.compile(expr).load();
    selector.setContextItem(ctx);
    XdmValue value = selector.evaluate();
    if (value == null || value.isEmptySequence()) {
      return null;
    }
    return value.itemAt(0).getStringValue();
  }

  private static XdmNode node(XdmNode ctx, XPathCompiler compiler) throws SaxonApiException {
    XPathSelector selector = compiler.compile("kml:Style").load();
    selector.setContextItem(ctx);
    XdmValue value = selector.evaluate();
    if (value == null || value.isEmptySequence()) {
      return null;
    }
    XdmItem item = value.itemAt(0);
    return (item instanceof XdmNode node) ? node : null;
  }

  private static String firstNonBlank(XdmNode context, XPathCompiler compiler, String... xpaths) {
    for (String xp : xpaths) {
      try {
        XPathSelector sel = compiler.compile(xp).load();
        sel.setContextItem(context);
        XdmValue value = sel.evaluate();

        if (value != null && !value.isEmptySequence()) {
          String s = value.itemAt(0).getStringValue();
          if (s != null) {
            s = s.trim();
            if (!s.isEmpty()) {
              return s;
            }
          }
        }
      } catch (Exception _) {
        // intentionally ignore and try next
      }
    }
    return null;
  }
}
