// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.extractor;

import com.opencop.core.sdk.business.model.integration.connection.material.certificate.CertificateStoreFormat;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputException;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputFailure;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

/**
 * A certificate store is usable only once it opens with the password supplied for it. Checking that
 * before anything is stored turns a corrupt file or a wrong password into a rejection the requester
 * can fix, rather than a connection that fails at its first handshake.
 *
 * <p>Shared by every extractor that accepts uploaded stores, so a store that does not open is
 * rejected the same way, naming its field, whichever integration declared it.
 *
 * <p>Opening a store costs time set by the iteration counts it declares, and a crafted file can
 * pack many encrypted sections, each paying that cost: a few dozen take tens of seconds. So a store
 * is read in bounded size, and the key derivation it declares is bounded before it is opened. Both
 * bounds are far above any real store: a client key and certificate take a few kilobytes, every
 * public certificate authority together about 130 KB, and exporters declare thousands of iterations
 * where the bound allows millions.
 */
public final class CertificateStoreOpening {

  public static final String UNREADABLE_CERTIFICATE_STORE = "UNREADABLE_CERTIFICATE_STORE";
  public static final String CERTIFICATE_STORE_TOO_LARGE = "CERTIFICATE_STORE_TOO_LARGE";
  public static final String CERTIFICATE_STORE_TOO_EXPENSIVE = "CERTIFICATE_STORE_TOO_EXPENSIVE";

  /** The largest certificate store accepted, in bytes. */
  public static final int MAX_STORE_BYTES = 256 * 1_024;

  /**
   * The most key-derivation iterations opening one store may declare, summed over its encrypted
   * sections and its integrity check. The JDK opens at most 5,000,000 for each, so a store at the
   * strongest settings it accepts, one encrypted section and its integrity check, fits.
   */
  public static final BigInteger MAX_OPENING_ITERATIONS = BigInteger.valueOf(10_000_000);

  private static final String UNREADABLE_MESSAGE =
      "The file supplied as '%s' is not a %s certificate store that opens with its password";

  private static final String TOO_LARGE_MESSAGE =
      "The file supplied as '%s' exceeds the " + MAX_STORE_BYTES + "-byte certificate store limit";

  private static final String TOO_EXPENSIVE_MESSAGE =
      "The file supplied as '%s' declares more than "
          + MAX_OPENING_ITERATIONS
          + " key-derivation iterations, more than a certificate store may take to open";

  private CertificateStoreOpening() {}

  /**
   * Reads a store's content, refusing it once it exceeds {@link #MAX_STORE_BYTES} rather than
   * reading all of it.
   *
   * @param field the field the store was supplied as, which a rejection names
   */
  public static byte[] readBounded(String field, InputStream content)
      throws IOException, ConnectionInputException {
    var bytes = content.readNBytes(MAX_STORE_BYTES + 1);
    if (bytes.length > MAX_STORE_BYTES) {
      throw ConnectionInputException.rejecting(
          field,
          ConnectionInputFailure.INVALID_INPUT,
          CERTIFICATE_STORE_TOO_LARGE,
          TOO_LARGE_MESSAGE.formatted(field));
    }
    return bytes;
  }

  /**
   * Requires the store's content to open with its password, refusing before opening it a store that
   * declares more key derivation than {@link #MAX_OPENING_ITERATIONS}.
   *
   * @param field the field the store was supplied as, which a rejection names
   */
  public static void requireOpenable(
      String field, byte[] content, String password, CertificateStoreFormat format)
      throws ConnectionInputException {
    BigInteger iterations;
    try {
      iterations = Pkcs12OpeningWork.declaredIterations(content);
    } catch (IllegalArgumentException _) {
      throw unreadable(field, format);
    }
    if (iterations.compareTo(MAX_OPENING_ITERATIONS) > 0) {
      throw ConnectionInputException.rejecting(
          field,
          ConnectionInputFailure.INVALID_INPUT,
          CERTIFICATE_STORE_TOO_EXPENSIVE,
          TOO_EXPENSIVE_MESSAGE.formatted(field));
    }
    if (!loads(content, password, format)) {
      throw unreadable(field, format);
    }
  }

  /**
   * Whether the store's content opens with its password, for a caller with no field to name. A
   * store declaring more key derivation than {@link #MAX_OPENING_ITERATIONS} does not, and is not
   * opened.
   */
  public static boolean opens(byte[] content, String password, CertificateStoreFormat format) {
    try {
      return Pkcs12OpeningWork.declaredIterations(content).compareTo(MAX_OPENING_ITERATIONS) <= 0
          && loads(content, password, format);
    } catch (IllegalArgumentException _) {
      return false;
    }
  }

  private static boolean loads(byte[] content, String password, CertificateStoreFormat format) {
    try {
      KeyStore.getInstance(format.runtimeName())
          .load(new ByteArrayInputStream(content), password.toCharArray());
      return true;
    } catch (IOException | GeneralSecurityException | IllegalArgumentException _) {
      return false;
    }
  }

  private static ConnectionInputException unreadable(String field, CertificateStoreFormat format) {
    return ConnectionInputException.rejecting(
        field,
        ConnectionInputFailure.INVALID_INPUT,
        UNREADABLE_CERTIFICATE_STORE,
        UNREADABLE_MESSAGE.formatted(field, format.runtimeName()));
  }
}
