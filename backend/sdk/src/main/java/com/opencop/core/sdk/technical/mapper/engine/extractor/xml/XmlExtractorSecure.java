// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.extractor.xml;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import net.sf.saxon.Configuration;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.s9api.Processor;

public final class XmlExtractorSecure {

  private XmlExtractorSecure() {}

  public static void configure(Processor processor) {
    try {
      Configuration config = processor.getUnderlyingConfiguration();
      config.setConfigurationProperty(FeatureKeys.ALLOW_EXTERNAL_FUNCTIONS, false);
      config.setConfigurationProperty(
          FeatureKeys.XML_PARSER_FEATURE + "http://apache.org/xml/features/disallow-doctype-decl",
          true);
      config.setConfigurationProperty(
          FeatureKeys.XML_PARSER_FEATURE + "http://xml.org/sax/features/external-general-entities",
          false);
      config.setConfigurationProperty(
          FeatureKeys.XML_PARSER_FEATURE
              + "http://xml.org/sax/features/external-parameter-entities",
          false);
      config.setConfigurationProperty(
          FeatureKeys.XML_PARSER_FEATURE
              + "http://apache.org/xml/features/nonvalidating/load-external-dtd",
          false);
    } catch (RuntimeException e) {
      throw new IllegalStateException("Failed to configure secure XML processing", e);
    }
  }

  /** Applies the same external-resource restrictions to DOM parsing. */
  public static void configure(DocumentBuilderFactory factory) {
    try {
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    } catch (ParserConfigurationException | IllegalArgumentException e) {
      throw new IllegalStateException("Failed to configure secure XML processing", e);
    }
  }
}
