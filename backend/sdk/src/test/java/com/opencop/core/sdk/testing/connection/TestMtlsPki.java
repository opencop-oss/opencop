// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.testing.connection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/** In-memory certificate authority and identities for mTLS tests. */
public final class TestMtlsPki {

  private static final String PASSWORD = "opencop-test-pki";
  private static final TestMtlsPki SHARED = generate();

  private final byte[] serverKeyStore;
  private final byte[] clientKeyStore;
  private final byte[] trustStore;
  private final String clientKeyStoreBase64;
  private final String trustStoreBase64;

  private TestMtlsPki() throws GeneralSecurityException, IOException, OperatorCreationException {
    Provider provider = new BouncyCastleProvider();
    SecureRandom secureRandom = new SecureRandom();
    KeyPair caKeyPair = keyPair(provider, secureRandom);
    KeyPair serverKeyPair = keyPair(provider, secureRandom);
    KeyPair clientKeyPair = keyPair(provider, secureRandom);

    Instant now = Instant.now();
    Date notBefore = Date.from(now.minus(5, ChronoUnit.MINUTES));
    Date notAfter = Date.from(now.plus(1, ChronoUnit.DAYS));
    X500Name caName = new X500Name("CN=OpenCOP Test CA");
    X509Certificate ca = caCertificate(provider, caName, caKeyPair, notBefore, notAfter);
    X509Certificate server =
        serverCertificate(provider, caName, caKeyPair, serverKeyPair, notBefore, notAfter);
    X509Certificate client =
        clientCertificate(provider, caName, caKeyPair, clientKeyPair, notBefore, notAfter);

    char[] password = PASSWORD.toCharArray();
    try {
      serverKeyStore = keyStore("server", serverKeyPair, server, ca, password);
      clientKeyStore = keyStore("client", clientKeyPair, client, ca, password);
      trustStore = trustStore(ca, password);
    } finally {
      Arrays.fill(password, '\0');
    }
    clientKeyStoreBase64 = Base64.getEncoder().encodeToString(clientKeyStore);
    trustStoreBase64 = Base64.getEncoder().encodeToString(trustStore);
  }

  public static TestMtlsPki shared() {
    return SHARED;
  }

  public InputStream serverKeyStore() {
    return new ByteArrayInputStream(serverKeyStore);
  }

  public InputStream clientKeyStore() {
    return new ByteArrayInputStream(clientKeyStore);
  }

  public InputStream trustStore() {
    return new ByteArrayInputStream(trustStore);
  }

  public String clientKeyStoreBase64() {
    return clientKeyStoreBase64;
  }

  public String trustStoreBase64() {
    return trustStoreBase64;
  }

  public String password() {
    return PASSWORD;
  }

  private static TestMtlsPki generate() {
    try {
      return new TestMtlsPki();
    } catch (GeneralSecurityException | IOException | OperatorCreationException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static KeyPair keyPair(Provider provider, SecureRandom secureRandom)
      throws GeneralSecurityException {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", provider);
    generator.initialize(2048, secureRandom);
    return generator.generateKeyPair();
  }

  private static X509Certificate caCertificate(
      Provider provider, X500Name caName, KeyPair caKeyPair, Date notBefore, Date notAfter)
      throws IOException, GeneralSecurityException, OperatorCreationException {
    var builder =
        new JcaX509v3CertificateBuilder(
            caName, BigInteger.ONE, notBefore, notAfter, caName, caKeyPair.getPublic());
    builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
    builder.addExtension(
        Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
    return certificate(provider, builder.build(signer(provider, caKeyPair)));
  }

  private static X509Certificate serverCertificate(
      Provider provider,
      X500Name caName,
      KeyPair caKeyPair,
      KeyPair serverKeyPair,
      Date notBefore,
      Date notAfter)
      throws IOException, GeneralSecurityException, OperatorCreationException {
    var builder =
        new JcaX509v3CertificateBuilder(
            caName,
            BigInteger.TWO,
            notBefore,
            notAfter,
            new X500Name("CN=localhost"),
            serverKeyPair.getPublic());
    builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
    builder.addExtension(
        Extension.keyUsage,
        true,
        new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
    builder.addExtension(
        Extension.extendedKeyUsage, false, new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
    builder.addExtension(
        Extension.subjectAlternativeName,
        false,
        new GeneralNames(
            new GeneralName[] {
              new GeneralName(GeneralName.dNSName, "localhost"),
              new GeneralName(GeneralName.iPAddress, "127.0.0.1")
            }));
    return certificate(provider, builder.build(signer(provider, caKeyPair)));
  }

  private static X509Certificate clientCertificate(
      Provider provider,
      X500Name caName,
      KeyPair caKeyPair,
      KeyPair clientKeyPair,
      Date notBefore,
      Date notAfter)
      throws IOException, GeneralSecurityException, OperatorCreationException {
    var builder =
        new JcaX509v3CertificateBuilder(
            caName,
            BigInteger.valueOf(3),
            notBefore,
            notAfter,
            new X500Name("CN=OpenCOP Test Client"),
            clientKeyPair.getPublic());
    builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
    builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
    builder.addExtension(
        Extension.extendedKeyUsage, false, new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth));
    return certificate(provider, builder.build(signer(provider, caKeyPair)));
  }

  private static ContentSigner signer(Provider provider, KeyPair issuer)
      throws OperatorCreationException {
    return new JcaContentSignerBuilder("SHA256withRSA")
        .setProvider(provider)
        .build(issuer.getPrivate());
  }

  private static X509Certificate certificate(
      Provider provider, X509CertificateHolder certificateHolder) throws GeneralSecurityException {
    return new JcaX509CertificateConverter()
        .setProvider(provider)
        .getCertificate(certificateHolder);
  }

  private static byte[] keyStore(
      String alias,
      KeyPair keyPair,
      X509Certificate certificate,
      X509Certificate ca,
      char[] password)
      throws GeneralSecurityException, IOException {
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    keyStore.load(null, password);
    keyStore.setKeyEntry(
        alias, keyPair.getPrivate(), password, new Certificate[] {certificate, ca});
    return serialize(keyStore, password);
  }

  private static byte[] trustStore(X509Certificate ca, char[] password)
      throws GeneralSecurityException, IOException {
    KeyStore trustStore = KeyStore.getInstance("PKCS12");
    trustStore.load(null, password);
    trustStore.setCertificateEntry("ca", ca);
    return serialize(trustStore, password);
  }

  private static byte[] serialize(KeyStore keyStore, char[] password)
      throws GeneralSecurityException, IOException {
    var output = new ByteArrayOutputStream();
    keyStore.store(output, password);
    return output.toByteArray();
  }
}
