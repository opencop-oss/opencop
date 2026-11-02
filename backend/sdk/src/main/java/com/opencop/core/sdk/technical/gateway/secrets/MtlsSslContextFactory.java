// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.secrets;

import com.opencop.core.sdk.model.connection.spec.security.KeystoreRef;
import com.opencop.core.sdk.model.connection.spec.security.MtlsConfig;
import com.opencop.core.sdk.ports.gateway.secrets.SecretReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * Builds the TLS context a connection authenticates with, from the client store and optional
 * truststore its mTLS security references, read through a {@link SecretReader}.
 *
 * <p>The client store must hold the connection's key. The truststore is optional: without stored
 * truststore material, the authority is taken from the client certificate's own chain.
 *
 * <p>The stores are opened with the BouncyCastle provider, which the SDK declares as an optional
 * dependency: a module using this factory declares {@code org.bouncycastle:bcprov-jdk18on} itself.
 */
public final class MtlsSslContextFactory {

  private static final String PROVIDER = BouncyCastleProvider.PROVIDER_NAME;
  private static final String MANAGER_ALGORITHM = "SunX509";
  private static final String PROTOCOL = "TLS";
  private static final String CONTENT_KEY = "b64";
  private static final String PASSWORD_KEY = "password";
  private static final String AUTHORITY_ALIAS = "ca-cert";
  private static final int NOT_AN_AUTHORITY = -1;

  private final SecretReader secrets;

  public MtlsSslContextFactory(SecretReader secrets) {
    this.secrets = secrets;
  }

  /**
   * @throws MtlsMaterialException when the client material is missing, any stored material cannot
   *     be read or opened, or no authority can be found to trust
   */
  public SSLContext create(MtlsConfig mtls) {
    Security.addProvider(new BouncyCastleProvider());
    try {
      var client =
          stored(mtls.keyStore())
              .orElseThrow(() -> new MtlsMaterialException("The client store material is missing"));
      var keyStore = open(KeyStore.getInstance(mtls.keyStore().type(), PROVIDER), client);
      var authority = authorityFromTruststore(mtls.trustStore());
      var trusted = authority.isPresent() ? authority.get() : authorityFromClientChain(keyStore);

      var keys = KeyManagerFactory.getInstance(MANAGER_ALGORITHM);
      keys.init(keyStore, client.get(PASSWORD_KEY).toCharArray());
      var trust = TrustManagerFactory.getInstance(MANAGER_ALGORITHM);
      trust.init(trusted);

      var context = SSLContext.getInstance(PROTOCOL);
      context.init(keys.getKeyManagers(), trust.getTrustManagers(), null);
      return context;
    } catch (MtlsMaterialException e) {
      throw e;
    } catch (GeneralSecurityException | IOException | RuntimeException e) {
      // Malformed stores surface as decoding, casting and empty-store errors as well as security
      // ones, and an unreachable store as its own error; none of them leaves usable material.
      throw new MtlsMaterialException("The mTLS material cannot be read or opened", e);
    }
  }

  /** The stored material a reference points at, when it names a store holding content. */
  private Optional<Map<String, String>> stored(KeystoreRef ref) {
    if (!references(ref)) {
      return Optional.empty();
    }
    return Optional.ofNullable(secrets.read(ref.uri())).filter(this::isComplete);
  }

  /** A client-only connection keeps a truststore reference without a URI, which names nothing. */
  private static boolean references(KeystoreRef ref) {
    return ref != null && ref.uri() != null && !ref.uri().isBlank();
  }

  private boolean isComplete(Map<String, String> material) {
    return material.containsKey(CONTENT_KEY) && material.containsKey(PASSWORD_KEY);
  }

  private static KeyStore open(KeyStore store, Map<String, String> material)
      throws GeneralSecurityException, IOException {
    store.load(
        new ByteArrayInputStream(Base64.getDecoder().decode(material.get(CONTENT_KEY))),
        material.get(PASSWORD_KEY).toCharArray());
    return store;
  }

  /**
   * A store trusting the last certificate entry of the stored truststore, or empty when there is no
   * stored truststore or it holds no certificate entry, in which case the client chain is used
   * instead. A referenced truststore's type must be valid even when nothing is stored for it.
   */
  private Optional<KeyStore> authorityFromTruststore(KeystoreRef ref)
      throws GeneralSecurityException, IOException {
    if (!references(ref)) {
      return Optional.empty();
    }
    var store = KeyStore.getInstance(ref.type(), PROVIDER);
    var material = stored(ref);
    if (material.isEmpty()) {
      return Optional.empty();
    }
    open(store, material.get());
    X509Certificate authority = null;
    var aliases = store.aliases();
    while (aliases.hasMoreElements()) {
      var alias = aliases.nextElement();
      if (store.isCertificateEntry(alias)) {
        authority = (X509Certificate) store.getCertificate(alias);
      }
    }
    if (authority == null) {
      return Optional.empty();
    }
    return Optional.of(trusting(store, authority));
  }

  private static KeyStore authorityFromClientChain(KeyStore keyStore)
      throws GeneralSecurityException, IOException {
    var alias = keyStore.aliases().nextElement();
    return trusting(
        KeyStore.getInstance(KeyStore.getDefaultType()),
        authorityIn(keyStore.getCertificateChain(alias)));
  }

  private static X509Certificate authorityIn(Certificate[] chain) {
    for (var certificate : chain) {
      if (certificate instanceof X509Certificate x509 && isAuthority(x509)) {
        return x509;
      }
    }
    throw new MtlsMaterialException("The client certificate chain holds no authority");
  }

  /**
   * Basic constraints are -1 for an end entity and a path length of zero or more for an authority.
   */
  private static boolean isAuthority(X509Certificate certificate) {
    return certificate.getBasicConstraints() != NOT_AN_AUTHORITY;
  }

  private static KeyStore trusting(KeyStore store, X509Certificate authority)
      throws GeneralSecurityException, IOException {
    store.load(null);
    store.setCertificateEntry(AUTHORITY_ALIAS, authority);
    return store;
  }
}
