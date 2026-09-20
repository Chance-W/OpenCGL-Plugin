package com.opencgl.lanmsg.security;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Persistent self-signed credential. A valid certificate is NOT authorization to exchange business data. */
public final class DeviceIdentity {
    private final String deviceId;
    private final X509Certificate certificate;
    private final KeyManager[] managers;

    private DeviceIdentity(String deviceId, X509Certificate certificate, KeyManager[] managers) {
        this.deviceId = deviceId; this.certificate = certificate; this.managers = managers;
    }

    public static DeviceIdentity loadOrCreate(LocalVault vault, String deviceId) throws IOException {
        canonicalId(deviceId);
        // Serialize read/create even if two controllers mistakenly share the same vault.
        synchronized (vault) {
            byte[] encoded = vault.read("device-identity", "v1").orElse(null);
            char[] password = new char[0]; // PKCS12 is always inside the authenticated encrypted vault.
            try {
                boolean firstCreation = vault.reserveDeviceIdentity(deviceId);
                if (!firstCreation && encoded == null)
                    throw new IOException("Device credential is missing; refusing to generate a replacement identity");
                if (firstCreation && encoded != null)
                    throw new IOException("Device initialization manifest is inconsistent; refusing to repair automatically");
                KeyStore keys = KeyStore.getInstance("PKCS12");
                if (encoded == null) {
                    keys.load(null, password);
                    var generator = KeyPairGenerator.getInstance("EC");
                    generator.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
                    var pair = generator.generateKeyPair();
                    var name = new X500Name("CN=" + deviceId);
                    Instant now = Instant.now();
                    var builder = new JcaX509v3CertificateBuilder(name,
                            new BigInteger(159, new SecureRandom()).add(BigInteger.ONE),
                            Date.from(now.minus(5, ChronoUnit.MINUTES)), Date.from(now.plus(3650, ChronoUnit.DAYS)),
                            name, pair.getPublic());
                    builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
                    builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
                    builder.addExtension(Extension.extendedKeyUsage, false,
                            new ExtendedKeyUsage(new KeyPurposeId[]{KeyPurposeId.id_kp_serverAuth, KeyPurposeId.id_kp_clientAuth}));
                    var cert = new JcaX509CertificateConverter().getCertificate(builder.build(
                            new JcaContentSignerBuilder("SHA256withECDSA").build(pair.getPrivate())));
                    keys.setKeyEntry("device", pair.getPrivate(), password, new java.security.cert.Certificate[]{cert});
                    var identity = validate(keys, password, deviceId);
                    try (var buffer = new SensitiveBuffer()) {
                        keys.store(buffer, password); encoded = buffer.toByteArray();
                    }
                    vault.write("device-identity", "v1", encoded);
                    return identity;
                }
                if (encoded.length > 32 * 1024) throw new IOException("Invalid device credential size");
                keys.load(new ByteArrayInputStream(encoded), password);
                return validate(keys, password, deviceId);
            } catch (IOException e) { throw e; }
            catch (Exception e) { throw new IOException("Device credential is invalid; it has not been replaced", e); }
            finally {
                if (encoded != null) Arrays.fill(encoded, (byte) 0);
                Arrays.fill(password, '\0');
            }
        }
    }

    private static DeviceIdentity validate(KeyStore keys, char[] password, String id) throws GeneralSecurityException {
        if (keys.size() != 1 || !keys.isKeyEntry("device")) throw new KeyStoreException("Invalid device key entry");
        if (!(keys.getCertificate("device") instanceof X509Certificate cert)
                || !id.equals(validateCertificate(cert))) throw new CertificateException("Device ID does not match credential");
        var chain = keys.getCertificateChain("device");
        if (chain == null || chain.length != 1) throw new CertificateException("Invalid device certificate chain");
        if (!(keys.getKey("device", password) instanceof PrivateKey privateKey)) throw new KeyStoreException("Device private key missing");
        byte[] challenge = new byte[32]; new SecureRandom().nextBytes(challenge);
        var signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(privateKey); signature.update(challenge); byte[] signed = signature.sign();
        signature.initVerify(cert.getPublicKey()); signature.update(challenge);
        if (!signature.verify(signed)) throw new KeyStoreException("Device private key does not match certificate");
        var factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        factory.init(keys, password);
        return new DeviceIdentity(id, cert, factory.getKeyManagers());
    }

    /** Structural validation only; the caller must additionally verify pinned trust and bilateral approval. */
    static String validateCertificate(X509Certificate cert) throws GeneralSecurityException {
        cert.checkValidity();
        if (!cert.getIssuerX500Principal().equals(cert.getSubjectX500Principal()) || cert.getBasicConstraints() != -1)
            throw new CertificateException("Expected a self-signed non-CA device certificate");
        if (!"SHA256withECDSA".equalsIgnoreCase(cert.getSigAlgName())) throw new CertificateException("Unsupported signature algorithm");
        if (!(cert.getPublicKey() instanceof ECPublicKey key)) throw new CertificateException("Expected EC device key");
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec expected = parameters.getParameterSpec(ECParameterSpec.class), actual = key.getParams();
        if (!actual.getCurve().equals(expected.getCurve()) || !actual.getGenerator().equals(expected.getGenerator())
                || !actual.getOrder().equals(expected.getOrder()) || actual.getCofactor() != expected.getCofactor())
            throw new CertificateException("Expected P-256 device key");
        boolean[] usage = cert.getKeyUsage();
        if (usage == null || !usage[0]) throw new CertificateException("Certificate cannot sign");
        var extended = cert.getExtendedKeyUsage();
        if (extended == null || !extended.contains(KeyPurposeId.id_kp_clientAuth.getId())
                || !extended.contains(KeyPurposeId.id_kp_serverAuth.getId()))
            throw new CertificateException("Missing TLS device usages");
        if (cert.hasUnsupportedCriticalExtension()) throw new CertificateException("Unsupported critical certificate extension");
        cert.verify(key);
        var subject = X500Name.getInstance(cert.getSubjectX500Principal().getEncoded());
        var rdns = subject.getRDNs();
        if (rdns.length != 1 || rdns[0].isMultiValued() || !rdns[0].getFirst().getType().equals(BCStyle.CN))
            throw new CertificateException("Invalid device certificate subject");
        String id = rdns[0].getFirst().getValue().toString();
        try { return canonicalId(id); }
        catch (IllegalArgumentException e) { throw new CertificateException("Invalid device UUID", e); }
    }

    static String canonicalId(String id) {
        if (id == null || !UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException("Expected canonical device UUID");
        return id;
    }

    public String deviceId() { return deviceId; }
    public X509Certificate certificate() { return certificate; }
    public String fingerprint() {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getPublicKey().getEncoded())); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
    public KeyManager[] keyManagers() { return managers.clone(); }

    private static final class SensitiveBuffer extends ByteArrayOutputStream {
        @Override public void close() { Arrays.fill(buf, (byte) 0); reset(); }
    }
}
