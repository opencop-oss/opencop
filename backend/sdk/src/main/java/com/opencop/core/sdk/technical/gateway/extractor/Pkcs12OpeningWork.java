// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.extractor;

import java.io.IOException;
import java.math.BigInteger;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.pkcs.AuthenticatedSafe;
import org.bouncycastle.asn1.pkcs.ContentInfo;
import org.bouncycastle.asn1.pkcs.EncryptedData;
import org.bouncycastle.asn1.pkcs.MacData;
import org.bouncycastle.asn1.pkcs.PBES2Parameters;
import org.bouncycastle.asn1.pkcs.PBKDF2Params;
import org.bouncycastle.asn1.pkcs.PKCS12PBEParams;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.Pfx;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;

/**
 * Reads, without opening a PKCS#12 store, how much key derivation opening it would take: the
 * iteration counts its encrypted sections and its integrity check declare.
 *
 * <p>Opening a store derives a key for every encrypted section and for the integrity check, before
 * it can tell whether the password is right. The JDK limits each count but not how many sections a
 * store holds, so a small crafted store can take minutes to open. Private keys are decrypted only
 * when used, not when the store is opened, so their counts are not read here.
 *
 * <p>Reads with Bouncy Castle's ASN.1 parser, which also reads the indefinite-length encoding some
 * exporters write. The SDK declares that library optional: a runtime that opens stores must declare
 * it itself, as Core does.
 */
final class Pkcs12OpeningWork {

  private Pkcs12OpeningWork() {}

  /**
   * Returns the iterations opening the store would derive keys with, summed over its encrypted
   * sections and its integrity check.
   *
   * @throws IllegalArgumentException when the content is not a PKCS#12 store protected the way the
   *     JDK opens one
   */
  static BigInteger declaredIterations(byte[] content) {
    try {
      var pfx = Pfx.getInstance(ASN1Primitive.fromByteArray(content));
      if (pfx == null) {
        throw new IllegalArgumentException("Empty PKCS#12 content");
      }
      var total = integrityIterations(pfx.getMacData());
      for (var section : sections(pfx.getAuthSafe())) {
        if (PKCSObjectIdentifiers.encryptedData.equals(section.getContentType())) {
          var encrypted = EncryptedData.getInstance(section.getContent());
          total = total.add(iterations(encrypted.getEncryptionAlgorithm()));
        }
      }
      return total;
    } catch (IOException | IllegalStateException | ClassCastException exception) {
      throw new IllegalArgumentException("Malformed PKCS#12 content", exception);
    }
  }

  private static BigInteger integrityIterations(MacData mac) {
    return mac == null ? BigInteger.ZERO : mac.getIterationCount();
  }

  /** The store's sections, which the JDK reads only from a password-integrity store. */
  private static ContentInfo[] sections(ContentInfo authenticatedSafe) throws IOException {
    if (!PKCSObjectIdentifiers.data.equals(authenticatedSafe.getContentType())) {
      throw new IllegalArgumentException("Unsupported PKCS#12 integrity mode");
    }
    var encoded = ASN1OctetString.getInstance(authenticatedSafe.getContent()).getOctets();
    return AuthenticatedSafe.getInstance(ASN1Primitive.fromByteArray(encoded)).getContentInfo();
  }

  /** The iteration count of a password-based encryption scheme the JDK decrypts sections with. */
  private static BigInteger iterations(AlgorithmIdentifier scheme) {
    var algorithm = scheme.getAlgorithm();
    if (algorithm.on(PKCSObjectIdentifiers.pkcs_12PbeIds)) {
      return PKCS12PBEParams.getInstance(scheme.getParameters()).getIterations();
    }
    if (PKCSObjectIdentifiers.id_PBES2.equals(algorithm)) {
      var derivation = PBES2Parameters.getInstance(scheme.getParameters()).getKeyDerivationFunc();
      if (PKCSObjectIdentifiers.id_PBKDF2.equals(derivation.getAlgorithm())) {
        return PBKDF2Params.getInstance(derivation.getParameters()).getIterationCount();
      }
    }
    throw new IllegalArgumentException("Unsupported PKCS#12 section encryption: " + algorithm);
  }
}
