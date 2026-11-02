// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.extension;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The path an extension operation answers on, relative to its extension: literal segments and whole
 * {@code {variable}} segments, such as {@code /subscriptions/{name}}. {@code /} answers on the
 * extension itself.
 */
public final class ExtensionPathTemplate {

  /**
   * Orders templates by segment count, then so that, at the first segment where they differ in
   * kind, a literal comes before a variable. Among the templates matching one path, the first is
   * the one to dispatch to.
   */
  public static final Comparator<ExtensionPathTemplate> MOST_SPECIFIC_FIRST =
      ExtensionPathTemplate::compareSpecificity;

  private static final String SEPARATOR = "/";
  private static final Pattern VARIABLE = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_]*)}");
  private static final Pattern RESERVED_CHARACTERS = Pattern.compile("[{}]");
  private static final List<String> DOT_SEGMENTS = List.of(".", "..");

  /** Stands for any variable in a shape; literals cannot contain braces, so it never collides. */
  private static final String ANY_VARIABLE = "{}";

  private final String template;
  private final List<Segment> segments;

  private ExtensionPathTemplate(String template, List<Segment> segments) {
    this.template = template;
    this.segments = segments;
  }

  /**
   * @throws IllegalArgumentException when the template does not start with {@code /}, has an empty
   *     or dot segment, a malformed or repeated variable, or braces inside a literal
   */
  public static ExtensionPathTemplate parse(String template) {
    if (template == null || !template.startsWith(SEPARATOR)) {
      throw invalid(template, "it must start with " + SEPARATOR);
    }
    if (template.equals(SEPARATOR)) {
      return new ExtensionPathTemplate(template, List.of());
    }
    var segments = new ArrayList<Segment>();
    var variableNames = new HashSet<String>();
    for (var text : template.substring(1).split(SEPARATOR, -1)) {
      var segment = segment(template, text);
      if (segment instanceof Segment.Variable(var name) && !variableNames.add(name)) {
        throw invalid(template, "variable {" + name + "} is repeated");
      }
      segments.add(segment);
    }
    return new ExtensionPathTemplate(template, List.copyOf(segments));
  }

  public String template() {
    return template;
  }

  /** The names of the template's variables, in path order. */
  public List<String> variableNames() {
    return segments.stream()
        .flatMap(
            segment ->
                segment instanceof Segment.Variable(var name) ? Stream.of(name) : Stream.empty())
        .toList();
  }

  /**
   * Matches decoded path segments against this template.
   *
   * @return each variable's segment by name, or empty when the segments do not match
   */
  public Optional<Map<String, String>> match(List<String> path) {
    if (path.size() != segments.size()) {
      return Optional.empty();
    }
    var variables = new LinkedHashMap<String, String>();
    for (int index = 0; index < segments.size(); index++) {
      var value = path.get(index);
      switch (segments.get(index)) {
        case Segment.Literal(var text) when !text.equals(value) -> {
          return Optional.empty();
        }
        case Segment.Literal _ -> {
          // A literal that matches captures nothing.
        }
        case Segment.Variable(var name) -> variables.put(name, value);
      }
    }
    return Optional.of(Map.copyOf(variables));
  }

  /**
   * Whether both templates match exactly the same paths, which is the case when they differ at most
   * in the names of their variables.
   */
  public boolean hasSameShapeAs(ExtensionPathTemplate other) {
    return shape().equals(other.shape());
  }

  @Override
  public String toString() {
    return template;
  }

  private List<String> shape() {
    return segments.stream()
        .map(
            segment ->
                switch (segment) {
                  case Segment.Literal(var text) -> text;
                  case Segment.Variable _ -> ANY_VARIABLE;
                })
        .toList();
  }

  private static int compareSpecificity(ExtensionPathTemplate first, ExtensionPathTemplate second) {
    // Templates of different lengths never match the same path; ordering them by length keeps the
    // order total, so a sort never lets an unrelated template hide a variable behind a literal.
    var byLength = Integer.compare(first.segments.size(), second.segments.size());
    if (byLength != 0) {
      return byLength;
    }
    for (int index = 0; index < first.segments.size(); index++) {
      var firstIsLiteral = first.segments.get(index) instanceof Segment.Literal;
      var secondIsLiteral = second.segments.get(index) instanceof Segment.Literal;
      if (firstIsLiteral != secondIsLiteral) {
        return firstIsLiteral ? -1 : 1;
      }
    }
    return 0;
  }

  private static Segment segment(String template, String text) {
    if (text.isEmpty()) {
      throw invalid(template, "it has an empty segment");
    }
    if (DOT_SEGMENTS.contains(text)) {
      throw invalid(template, "it has a dot segment");
    }
    var variable = VARIABLE.matcher(text);
    if (variable.matches()) {
      return new Segment.Variable(variable.group(1));
    }
    if (RESERVED_CHARACTERS.matcher(text).find()) {
      throw invalid(template, "segment '" + text + "' is neither a literal nor a whole {variable}");
    }
    return new Segment.Literal(text);
  }

  private static IllegalArgumentException invalid(String template, String reason) {
    return new IllegalArgumentException(
        "Invalid extension path template '" + template + "': " + reason);
  }

  private sealed interface Segment {

    record Literal(String text) implements Segment {}

    record Variable(String name) implements Segment {}
  }
}
