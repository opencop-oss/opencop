// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.extractor.xml;

import com.opencop.core.sdk.model.mapper.config.KmlFormatConfig;
import com.opencop.core.sdk.model.mapper.config.XmlFormatConfig;
import com.opencop.core.sdk.model.mapper.profile.DynamicMapper;
import com.opencop.core.sdk.model.mapper.rules.MappingRule;
import com.opencop.core.sdk.model.mapper.source.XPathSource;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import com.opencop.core.sdk.technical.mapper.engine.extractor.RecordExtractor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.databind.ObjectMapper;

public class XmlExtractor implements RecordExtractor<XdmNode> {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String KV_PREFIX = "xml:kvJson(";

  private final Processor processor;
  private final XPathCompiler compiler;
  private final String recordXPath;
  // cache: xpath string -> compiled executable
  private final ConcurrentHashMap<String, XPathExecutable> cache = new ConcurrentHashMap<>();

  private XPathExecutable compiled(String xpath) throws MapperEngineException {
    try {
      return cache.computeIfAbsent(
          xpath,
          executableXPath -> {
            try {
              return compiler.compile(executableXPath);
            } catch (SaxonApiException e) {
              throw new RuntimeException(e);
            }
          });
    } catch (RuntimeException e) {
      // unwrap compile error
      if (e.getCause() instanceof SaxonApiException se) {
        throw new MapperEngineException(
            "XPATH_COMPILE_ERROR", "An error occurend while compiling the xpath: " + xpath, se);
      }
      throw new MapperEngineException(
          "XPATH_COMPILE_ERROR", "An error occurend while compiling the xpath: " + xpath, e);
    }
  }

  private XmlExtractor(Map<String, String> namespaces, String recordXPath) {
    this.processor = new Processor(false);
    XmlExtractorSecure.configure(processor);
    this.compiler = processor.newXPathCompiler();

    namespaces.forEach(compiler::declareNamespace);
    this.recordXPath = recordXPath;
  }

  public static XmlExtractor fromProfile(DynamicMapper profile) {
    Map<String, String> namespaces = Map.of();
    if (profile.formatConfig() != null && profile.formatConfig().format() != null) {
      if (profile.formatConfig() instanceof XmlFormatConfig config) {
        namespaces = config.namespaces();
      } else if (profile.formatConfig() instanceof KmlFormatConfig config) {
        namespaces = config.namespaces();
      }
    }

    return new XmlExtractor(namespaces, ((XPathSource) profile.recordSelector().source()).expr());
  }

  private XdmNode parse(byte[] xmlBytes) throws MapperEngineException {
    try {
      return processor
          .newDocumentBuilder()
          .build(new StreamSource(new ByteArrayInputStream(xmlBytes)));
    } catch (Exception e) {
      throw new MapperEngineException("XML_PARSING_ERROR", "Failed to parse XML", e);
    }
  }

  @Override
  public byte[] toBytes(XdmNode recordEntry) {
    try {
      Processor p = recordEntry.getProcessor();
      Serializer ser = p.newSerializer();
      var out = new ByteArrayOutputStream();
      ser.setOutputStream(out);
      ser.serializeNode(recordEntry);
      return out.toByteArray();
    } catch (Exception _) {
      return null;
    }
  }

  @Override
  public List<XdmNode> selectRecords(byte[] payload, DynamicMapper profile)
      throws MapperEngineException {
    try {
      XPathSelector selector = compiler.compile(recordXPath).load();
      selector.setContextItem(parse(payload));

      List<XdmNode> nodes = new ArrayList<>();
      for (XdmItem item : selector.evaluate()) {
        if (item instanceof XdmNode node) {
          nodes.add(node);
        }
      }
      return nodes;
    } catch (Exception e) {
      throw new MapperEngineException(
          "XPATH_EVAL_ERROR", "Failed to evaluate XPath: " + recordXPath, e);
    }
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

    // Virtual expression handled by XmlExtractor itself
    if (isKvJsonExpr(expr)) {
      return evalKvJson(context, expr);
    }

    // Normal XPath behavior
    try {
      XPathSelector selector = compiled(expr).load();
      selector.setContextItem(context);
      XdmValue result = selector.evaluate();
      if (result.isEmptySequence()) {
        return null;
      }
      return result.itemAt(0).getStringValue();
    } catch (SaxonApiException e) {
      throw new MapperEngineException("XPATH_ERROR", "XPath eval failed: " + expr, e);
    }
  }

  private boolean isKvJsonExpr(String expr) {
    return expr != null && expr.startsWith(KV_PREFIX) && expr.endsWith(")");
  }

  private static String stripKvJsonWrapper(String expr) {
    // "xml:kvJson(...)" -> "..."
    return expr.substring(KV_PREFIX.length(), expr.length() - 1).trim();
  }

  private String evalKvJson(XdmNode context, String expr) throws MapperEngineException {
    String args = stripKvJsonWrapper(expr);
    Map<String, String> kvArgs = parseNamedArgs(args);

    String listXpath = kvArgs.get("list");
    String keyXpath = kvArgs.get("key");
    String valXpath = kvArgs.getOrDefault("value", "string(.)");

    if (listXpath == null || listXpath.isBlank()) {
      throw new MapperEngineException("XML_KVJSON_ERROR", "kvJson requires list=...", null);
    }
    if (keyXpath == null || keyXpath.isBlank()) {
      throw new MapperEngineException("XML_KVJSON_ERROR", "kvJson requires key=...", null);
    }

    try {
      XPathSelector listSel = compiled(listXpath).load();
      listSel.setContextItem(context);
      XdmValue nodes = listSel.evaluate();
      if (nodes == null || nodes.isEmptySequence()) {
        return null;
      }

      var map = new LinkedHashMap<String, String>();

      for (XdmItem item : nodes) {
        if (!(item instanceof XdmNode node)) {
          continue;
        }

        String key = evalFirstString(node, keyXpath);
        if (key == null) {
          continue;
        }

        key = key.trim();
        if (key.isEmpty()) {
          continue;
        }

        String value = evalFirstString(node, valXpath);
        if (value != null) {
          value = value.trim();
        }

        map.put(key, value);
      }

      if (map.isEmpty()) {
        return null;
      }
      return JSON.writeValueAsString(map);

    } catch (SaxonApiException e) {
      throw new MapperEngineException("XPATH_ERROR", "XPath eval failed inside kvJson: " + expr, e);
    } catch (Exception e) {
      throw new MapperEngineException("XML_KVJSON_ERROR", "Failed to build kvJson output", e);
    }
  }

  private static Map<String, String> parseNamedArgs(String args) throws MapperEngineException {
    Map<String, String> out = new LinkedHashMap<>();
    int i = 0;

    while (i < args.length()) {
      while (i < args.length() && (Character.isWhitespace(args.charAt(i)) || args.charAt(i) == ','))
        i++;
      if (i >= args.length()) break;

      // read name
      int startName = i;
      while (i < args.length()
          && (Character.isLetterOrDigit(args.charAt(i))
              || args.charAt(i) == '_'
              || args.charAt(i) == '-')) {
        i++;
      }
      if (i == startName) {
        throw new MapperEngineException(
            "XML_KVJSON_ERROR", "Invalid kvJson arg name near: " + args.substring(i), null);
      }
      String name = args.substring(startName, i);

      // skip whitespace
      while (i < args.length() && Character.isWhitespace(args.charAt(i))) {
        i++;
      }

      // expect =
      if (i >= args.length() || args.charAt(i) != '=') {
        throw new MapperEngineException(
            "XML_KVJSON_ERROR", "Expected '=' after '" + name + "'", null);
      }
      i++; // skip =

      // skip whitespace
      while (i < args.length() && Character.isWhitespace(args.charAt(i))) {
        i++;
      }

      if (i >= args.length() || (args.charAt(i) != '"' && args.charAt(i) != '\'')) {
        throw new MapperEngineException(
            "XML_KVJSON_ERROR", "Expected quoted value for '" + name + "'", null);
      }
      char quote = args.charAt(i++);
      int startVal = i;

      while (i < args.length() && args.charAt(i) != quote) i++;
      if (i >= args.length()) {
        throw new MapperEngineException(
            "XML_KVJSON_ERROR", "Unterminated quoted value for '" + name + "'", null);
      }

      String value = args.substring(startVal, i);
      i++; // skip closing quote

      out.put(name, value);
    }

    return out;
  }

  private String evalFirstString(XdmNode context, String xpath) throws MapperEngineException {
    // Force XPath to return at most one item, then convert to string
    String safe = "string((" + xpath + ")[1])";

    try {
      XPathSelector selector = compiled(safe).load();
      selector.setContextItem(context);
      XdmValue v = selector.evaluate();
      if (v == null || v.isEmptySequence()) return null;

      String s = v.itemAt(0).getStringValue();
      return (s == null || s.isBlank()) ? null : s;
    } catch (SaxonApiException e) {
      throw new MapperEngineException("XPATH_ERROR", "XPath eval failed: " + xpath, e);
    }
  }
}
