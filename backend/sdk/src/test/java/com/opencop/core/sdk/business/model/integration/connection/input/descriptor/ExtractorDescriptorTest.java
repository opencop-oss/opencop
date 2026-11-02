// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.input.descriptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.tuple;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExtractorDescriptorTest {

  private static final UUID ID = UUID.randomUUID();
  private static final Set<String> NO_FORMATS = Set.of();

  @Test
  void expressesCredentialsAsPlainAndSecretText() {
    var descriptor =
        new ExtractorDescriptor(
            ID,
            "Username and password",
            List.of(
                new TextField("username", "Username", true),
                new SecretField("password", "Password", true)));

    assertThat(descriptor.fields())
        .extracting(ExtractorField::name, ExtractorField::kind)
        .containsExactly(
            tuple("username", ExtractorFieldKind.TEXT),
            tuple("password", ExtractorFieldKind.SECRET));
  }

  @Test
  void expressesASingleArchiveUploadDeclaringSeveralMediaTypesForOneExtension() {
    var field =
        new FileField(
            "package",
            "Connection package",
            true,
            Set.of(".zip"),
            Set.of("application/zip", "application/x-zip-compressed"));

    var descriptor = new ExtractorDescriptor(ID, "TAK package", List.of(field));

    assertThat(descriptor.id()).isEqualTo(ID);
    assertThat(field.extensions()).containsExactly(".zip");
    assertThat(field.mediaTypes())
        .containsExactlyInAnyOrder("application/zip", "application/x-zip-compressed");
  }

  @Test
  void expressesAMultiFileCertificateUploadDeclaringAlternativeExtensions() {
    var field =
        new FileListField(
            "keyStore",
            "Certificate files",
            true,
            Set.of(".p12", ".pfx"),
            Set.of("application/x-pkcs12"));

    var descriptor = new ExtractorDescriptor(ID, "Client certificates", List.of(field));

    assertThat(descriptor.fields()).containsExactly(field);
    assertThat(field.extensions()).containsExactlyInAnyOrder(".p12", ".pfx");
  }

  @Test
  void rejectsABlankLabelOrFieldName() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ExtractorDescriptor(ID, " ", List.of(text("a"))));
    assertThatIllegalArgumentException().isThrownBy(() -> new TextField(" ", "Username", true));
    assertThatIllegalArgumentException().isThrownBy(() -> new TextField("username", " ", true));
  }

  @Test
  void rejectsAMissingIdentifier() {
    assertThatNullPointerException()
        .isThrownBy(() -> new ExtractorDescriptor(null, "Label", List.of(text("a"))))
        .withMessageContaining("id");
  }

  @Test
  void rejectsADescriptorThatDeclaresNoField() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ExtractorDescriptor(ID, "Label", List.of()))
        .withMessageContaining("at least one field");
  }

  @Test
  void rejectsDuplicateFieldNamesWithinOneDescriptor() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new ExtractorDescriptor(ID, "Label", List.of(text("username"), text("username"))))
        .withMessageContaining("username");
  }

  @Test
  void keepsFieldNamesCaseSensitiveSoMultipartRoleSpellingsRemainNatural() {
    var descriptor = new ExtractorDescriptor(ID, "Label", List.of(text("keyStore")));

    assertThat(descriptor.fields()).extracting(ExtractorField::name).containsExactly("keyStore");
  }

  @Test
  void requiresAtLeastOneExtensionOnFileKinds() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new FileField("package", "Package", true, NO_FORMATS, Set.of("application/zip")))
        .withMessageContaining("at least one extension");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new FileListField("packages", "Packages", true, NO_FORMATS, NO_FORMATS));
  }

  @Test
  void acceptsFileKindsWithoutAnyDeclaredMediaType() {
    var field = new FileField("preferences", "Preferences", false, Set.of(".pref"), NO_FORMATS);

    assertThat(field.mediaTypes()).isEmpty();
    assertThat(field.required()).isFalse();
  }

  @Test
  void cannotDeclareFormatsOnAScalarKindBecauseThereIsNowhereToPutThem() {
    assertThat(new TextField("username", "Username", true).kind())
        .isEqualTo(ExtractorFieldKind.TEXT);
    assertThat(new SecretField("password", "Password", true).kind())
        .isEqualTo(ExtractorFieldKind.SECRET);
    assertThat(new FileField("package", "Package", true, Set.of(".zip"), NO_FORMATS).kind())
        .isEqualTo(ExtractorFieldKind.FILE);
    assertThat(new FileListField("certs", "Certs", true, Set.of(".p12"), NO_FORMATS).kind())
        .isEqualTo(ExtractorFieldKind.FILE_LIST);
  }

  @Test
  void rejectsAnExtensionThatIsNotLowercaseAndDotPrefixed() {
    assertThatIllegalArgumentException().isThrownBy(() -> fileField(Set.of(" "), NO_FORMATS));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> fileField(Set.of("zip"), NO_FORMATS))
        .withMessageContaining("dot-prefixed");
    assertThatIllegalArgumentException().isThrownBy(() -> fileField(Set.of(".ZIP"), NO_FORMATS));
  }

  @Test
  void rejectsAMediaTypeThatIsNotTypeSlashSubtype() {
    assertThatIllegalArgumentException().isThrownBy(() -> fileField(Set.of(".zip"), Set.of(" ")));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> fileField(Set.of(".zip"), Set.of("application")))
        .withMessageContaining("type/subtype");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> fileField(Set.of(".zip"), Set.of("application / zip")));
  }

  @Test
  void rejectsMissingRequiredStructure() {
    assertThatNullPointerException()
        .isThrownBy(() -> new FileField("package", "Package", true, null, NO_FORMATS));
    assertThatNullPointerException()
        .isThrownBy(() -> new FileField("package", "Package", true, Set.of(".zip"), null));
    assertThatNullPointerException().isThrownBy(() -> new ExtractorDescriptor(ID, "Label", null));
  }

  @Test
  void copiesTheFieldListAndBothFormatSetsDefensively() {
    var extensions = new HashSet<>(Set.of(".zip"));
    var mediaTypes = new HashSet<>(Set.of("application/zip"));
    var field = new FileField("package", "Package", true, extensions, mediaTypes);
    var fields = new ArrayList<ExtractorField>(List.of(field));
    var descriptor = new ExtractorDescriptor(ID, "TAK package", fields);

    extensions.add(".tar");
    mediaTypes.add("application/x-tar");
    fields.add(text("username"));

    assertThat(field.extensions()).containsExactly(".zip");
    assertThat(field.mediaTypes()).containsExactly("application/zip");
    assertThat(descriptor.fields()).containsExactly(field);
    assertThatExceptionOfType(UnsupportedOperationException.class)
        .isThrownBy(() -> descriptor.fields().add(text("username")));
    assertThatExceptionOfType(UnsupportedOperationException.class)
        .isThrownBy(() -> field.extensions().add(".tar"));
    assertThatExceptionOfType(UnsupportedOperationException.class)
        .isThrownBy(() -> field.mediaTypes().add("application/x-tar"));
  }

  @Test
  void iteratesBothFormatSetsInAscendingOrderWhateverOrderTheCallerSupplies() {
    var extensions = new LinkedHashSet<>(List.of(".pfx", ".p12", ".jks"));
    var mediaTypes = new LinkedHashSet<>(List.of("application/zip", "application/x-pkcs12"));

    var field = new FileListField("package", "Package", true, extensions, mediaTypes);

    assertThat(field.extensions()).containsExactly(".jks", ".p12", ".pfx");
    assertThat(field.mediaTypes()).containsExactly("application/x-pkcs12", "application/zip");
  }

  private static ExtractorField fileField(Set<String> extensions, Set<String> mediaTypes) {
    return new FileField("package", "Package", true, extensions, mediaTypes);
  }

  private static ExtractorField text(String name) {
    return new TextField(name, "Label for " + name, true);
  }
}
