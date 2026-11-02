// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.extractor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.testing.connection.TestCertificateStores;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.util.List;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.BEROctetString;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.AuthenticatedSafe;
import org.bouncycastle.asn1.pkcs.ContentInfo;
import org.bouncycastle.asn1.pkcs.EncryptedData;
import org.bouncycastle.asn1.pkcs.EncryptionScheme;
import org.bouncycastle.asn1.pkcs.KeyDerivationFunc;
import org.bouncycastle.asn1.pkcs.MacData;
import org.bouncycastle.asn1.pkcs.PBES2Parameters;
import org.bouncycastle.asn1.pkcs.PBKDF2Params;
import org.bouncycastle.asn1.pkcs.PKCS12PBEParams;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.Pfx;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.DigestInfo;
import org.junit.jupiter.api.Test;

class Pkcs12OpeningWorkTest {

  private static final byte[] SALT = new byte[16];
  private static final byte[] CIPHERTEXT = new byte[48];
  private static final int AES_256_KEY_BYTES = 32;

  /** Both schemes the JDK decrypts sections with, and the integrity check, are counted. */
  @Test
  void sumsTheEncryptedSectionsAndTheIntegrityCheck() {
    var store =
        pfx(
            List.of(pbes2Section(3_000_000), pbes2Section(2_000_000), pkcs12PbeSection(4_000_000)),
            1_000_000,
            ASN1Encoding.DER);

    assertThat(Pkcs12OpeningWork.declaredIterations(store))
        .isEqualTo(BigInteger.valueOf(10_000_000));
  }

  /** Some exporters write PKCS#12 with indefinite lengths, which must read the same. */
  @Test
  void readsTheIndefiniteLengthEncoding() {
    var store = pfx(List.of(pbes2Section(3_000_000)), 1_000_000, ASN1Encoding.BER);

    assertThat(store[1]).isEqualTo((byte) 0x80);
    assertThat(Pkcs12OpeningWork.declaredIterations(store))
        .isEqualTo(BigInteger.valueOf(4_000_000));
  }

  /**
   * A section left unencrypted holds keys decrypted only when used, so it costs nothing to open.
   */
  @Test
  void countsNoUnencryptedSection() {
    var store = pfx(List.of(unencryptedSection(), pbes2Section(10_000)), 0, ASN1Encoding.DER);

    assertThat(Pkcs12OpeningWork.declaredIterations(store)).isEqualTo(BigInteger.valueOf(10_000));
  }

  /** The strongest store the JDK opens, one section and its integrity check, fits the budget. */
  @Test
  void admitsTheStrongestStoreTheJdkOpens() {
    var strongest = pfx(List.of(pbes2Section(5_000_000)), 5_000_000, ASN1Encoding.DER);
    var past = pfx(List.of(pbes2Section(5_000_000)), 5_000_001, ASN1Encoding.DER);

    assertThat(Pkcs12OpeningWork.declaredIterations(strongest))
        .isEqualTo(CertificateStoreOpening.MAX_OPENING_ITERATIONS);
    assertThat(Pkcs12OpeningWork.declaredIterations(past))
        .isGreaterThan(CertificateStoreOpening.MAX_OPENING_ITERATIONS);
  }

  @Test
  void admitsAStoreTheJdkWritesByDefault() {
    assertThat(Pkcs12OpeningWork.declaredIterations(TestCertificateStores.pkcs12("secret")))
        .isLessThanOrEqualTo(CertificateStoreOpening.MAX_OPENING_ITERATIONS);
  }

  @Test
  void rejectsASectionEncryptedWithASchemeTheJdkDoesNotDecrypt() {
    var unknown = section(new AlgorithmIdentifier(new ASN1ObjectIdentifier("1.2.3.4")));
    var store = pfx(List.of(unknown), 0, ASN1Encoding.DER);

    assertThatThrownBy(() -> Pkcs12OpeningWork.declaredIterations(store))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("1.2.3.4");
  }

  @Test
  void rejectsContentThatIsNoPkcs12Store() {
    assertThatThrownBy(() -> Pkcs12OpeningWork.declaredIterations(new byte[] {1, 2, 3}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Pkcs12OpeningWork.declaredIterations(new byte[0]))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static ContentInfo pbes2Section(int iterations) {
    var derivation =
        new KeyDerivationFunc(
            PKCSObjectIdentifiers.id_PBKDF2,
            new PBKDF2Params(
                SALT,
                iterations,
                AES_256_KEY_BYTES,
                new AlgorithmIdentifier(
                    PKCSObjectIdentifiers.id_hmacWithSHA256, DERNull.INSTANCE)));
    var encryption =
        new EncryptionScheme(NISTObjectIdentifiers.id_aes256_CBC, new DEROctetString(SALT));
    return section(
        new AlgorithmIdentifier(
            PKCSObjectIdentifiers.id_PBES2, new PBES2Parameters(derivation, encryption)));
  }

  private static ContentInfo pkcs12PbeSection(int iterations) {
    return section(
        new AlgorithmIdentifier(
            PKCSObjectIdentifiers.pbeWithSHAAnd3_KeyTripleDES_CBC,
            new PKCS12PBEParams(SALT, iterations)));
  }

  private static ContentInfo section(AlgorithmIdentifier encryption) {
    return new ContentInfo(
        PKCSObjectIdentifiers.encryptedData,
        new EncryptedData(PKCSObjectIdentifiers.data, encryption, new DEROctetString(CIPHERTEXT)));
  }

  private static ContentInfo unencryptedSection() {
    return new ContentInfo(PKCSObjectIdentifiers.data, new DEROctetString(new byte[] {0x30, 0x00}));
  }

  private static byte[] pfx(List<ContentInfo> sections, int integrityIterations, String encoding) {
    try {
      var authenticatedSafe =
          new AuthenticatedSafe(sections.toArray(ContentInfo[]::new)).getEncoded(encoding);
      var content =
          ASN1Encoding.BER.equals(encoding)
              ? new BEROctetString(authenticatedSafe)
              : new DEROctetString(authenticatedSafe);
      var integrity =
          integrityIterations == 0
              ? null
              : new MacData(
                  new DigestInfo(
                      new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256, DERNull.INSTANCE),
                      new byte[32]),
                  SALT,
                  integrityIterations);
      return new Pfx(new ContentInfo(PKCSObjectIdentifiers.data, content), integrity)
          .getEncoded(encoding);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}
