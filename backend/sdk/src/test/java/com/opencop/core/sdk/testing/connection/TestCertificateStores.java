// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.testing.connection;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import javax.crypto.KeyGenerator;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.pkcs.AuthenticatedSafe;
import org.bouncycastle.asn1.pkcs.ContentInfo;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.Pfx;

/**
 * Certificate stores built in memory, so tests exercise real PKCS#12 content without committing key
 * material. A store opens only with the password it was written with, which is what certificate
 * checks before storage rely on.
 */
public final class TestCertificateStores {

  private static final String STORE_TYPE = "PKCS12";
  private static final String ENTRY_ALIAS = "test";
  private static final String KEY_ALGORITHM = "AES";
  private static final String CERTIFICATE_ITERATIONS_PROPERTY =
      "keystore.pkcs12.certPbeIterationCount";

  private TestCertificateStores() {}

  /** A PKCS#12 store that opens with the password. */
  public static byte[] pkcs12(String password) {
    try {
      var store = KeyStore.getInstance(STORE_TYPE);
      store.load(null, null);
      var protection = new KeyStore.PasswordProtection(password.toCharArray());
      store.setEntry(
          ENTRY_ALIAS,
          new KeyStore.SecretKeyEntry(KeyGenerator.getInstance(KEY_ALGORITHM).generateKey()),
          protection);
      var content = new ByteArrayOutputStream();
      store.store(content, password.toCharArray());
      return content.toByteArray();
    } catch (IOException | GeneralSecurityException exception) {
      throw new IllegalStateException("Could not build a test certificate store", exception);
    }
  }

  /**
   * A PKCS#12 store holding one certificate, in an encrypted section whose key is derived with the
   * iteration count, that opens with the password.
   */
  public static byte[] pkcs12WithEncryptedCertificate(String password, int iterations) {
    var previous = System.getProperty(CERTIFICATE_ITERATIONS_PROPERTY);
    System.setProperty(CERTIFICATE_ITERATIONS_PROPERTY, Integer.toString(iterations));
    try {
      var store = KeyStore.getInstance(STORE_TYPE);
      store.load(null, null);
      store.setCertificateEntry(ENTRY_ALIAS, anyCertificateAuthority());
      var content = new ByteArrayOutputStream();
      store.store(content, password.toCharArray());
      return content.toByteArray();
    } catch (IOException | GeneralSecurityException exception) {
      throw new IllegalStateException("Could not build a test certificate store", exception);
    } finally {
      if (previous == null) {
        System.clearProperty(CERTIFICATE_ITERATIONS_PROPERTY);
      } else {
        System.setProperty(CERTIFICATE_ITERATIONS_PROPERTY, previous);
      }
    }
  }

  /**
   * The store with each of its encrypted sections repeated, and no integrity check, which is
   * optional: a crafted store that still opens, paying each section's key derivation again.
   */
  public static byte[] withEncryptedSectionsRepeated(byte[] store, int times) {
    try {
      var pfx = Pfx.getInstance(ASN1Primitive.fromByteArray(store));
      var sections = new ArrayList<ContentInfo>();
      for (var section :
          AuthenticatedSafe.getInstance(
                  ASN1OctetString.getInstance(pfx.getAuthSafe().getContent()).getOctets())
              .getContentInfo()) {
        var copies =
            PKCSObjectIdentifiers.encryptedData.equals(section.getContentType()) ? times : 1;
        sections.addAll(Collections.nCopies(copies, section));
      }
      var authenticatedSafe = new AuthenticatedSafe(sections.toArray(ContentInfo[]::new));
      return new Pfx(
              new ContentInfo(
                  PKCSObjectIdentifiers.data,
                  new DEROctetString(authenticatedSafe.getEncoded(ASN1Encoding.DER))),
              null)
          .getEncoded(ASN1Encoding.DER);
    } catch (IOException exception) {
      throw new IllegalStateException("Could not craft a test certificate store", exception);
    }
  }

  private static Certificate anyCertificateAuthority()
      throws IOException, GeneralSecurityException {
    var authorities =
        KeyStore.getInstance(
            new File(System.getProperty("java.home"), "lib/security/cacerts"), (char[]) null);
    return authorities.getCertificate(authorities.aliases().nextElement());
  }
}
