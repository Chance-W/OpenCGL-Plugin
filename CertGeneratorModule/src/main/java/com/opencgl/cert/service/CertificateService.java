package com.opencgl.cert.service;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Service for generating and managing X.509 Certificates using Bouncy Castle.
 */
public class CertificateService {

    private static final Logger logger = LoggerFactory.getLogger(CertificateService.class);
    private static final String BC_PROVIDER = "BC";

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * Generate a KeyPair (RSA or EC).
     */
    public KeyPair generateKeyPair(String algorithm, int keySize) throws Exception {
        logger.info("Generating {} KeyPair with size {}", algorithm, keySize);
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(algorithm, BC_PROVIDER);
        
        if ("EC".equalsIgnoreCase(algorithm)) {
            keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1")); // Default EC curve
        } else {
            keyPairGenerator.initialize(keySize, new SecureRandom());
        }
        
        return keyPairGenerator.generateKeyPair();
    }

    /**
     * Generate a Self-Signed Certificate (Root CA or Self-Signed End Entity).
     */
    public X509Certificate generateSelfSignedCert(KeyPair keyPair, String subjectDn, int days, List<String> sans, boolean isCa) throws Exception {
        return generateSignedCert(keyPair, keyPair.getPrivate(), subjectDn, subjectDn, days, sans, isCa);
    }

    /**
     * Generate a Certificate signed by an Issuer (CA).
     */
    public X509Certificate generateSignedCert(KeyPair subjectKeyPair, PrivateKey issuerPrivateKey, String subjectDn, String issuerDn, int days, List<String> sans, boolean isCa) throws Exception {
        long now = System.currentTimeMillis();
        Date startDate = new Date(now);
        Date endDate = new Date(now + (long) days * 24 * 60 * 60 * 1000);

        BigInteger serialNumber = new BigInteger(64, new SecureRandom());
        X500Name subjectName = new X500Name(subjectDn);
        X500Name issuerName = new X500Name(issuerDn);

        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuerName,
                serialNumber,
                startDate,
                endDate,
                subjectName,
                subjectKeyPair.getPublic()
        );

        // Add Extensions
        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();

        // Basic Constraints (isCA)
        certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(isCa));

        // Key Usage
        int usage = KeyUsage.digitalSignature | KeyUsage.keyEncipherment;
        if (isCa) {
            usage |= KeyUsage.keyCertSign | KeyUsage.cRLSign;
        }
        certBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(usage));

        // Subject Key Identifier
        certBuilder.addExtension(Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(subjectKeyPair.getPublic()));

        // Subject Alternative Names (SAN)
        if (sans != null && !sans.isEmpty()) {
            List<GeneralName> generalNames = new ArrayList<>();
            for (String san : sans) {
                if (isValidIp(san)) {
                    generalNames.add(new GeneralName(GeneralName.iPAddress, san));
                } else {
                    generalNames.add(new GeneralName(GeneralName.dNSName, san));
                }
            }
            if (!generalNames.isEmpty()) {
                certBuilder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(generalNames.toArray(new GeneralName[0])));
            }
        }

        // Sign
        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WithRSAEncryption").setProvider(BC_PROVIDER).build(issuerPrivateKey);
        X509CertificateHolder certHolder = certBuilder.build(contentSigner);

        return new JcaX509CertificateConverter().setProvider(BC_PROVIDER).getCertificate(certHolder);
    }
    
    private boolean isValidIp(String ip) {
        // Simple IP validation check
        return ip.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");
    }

    /**
     * Save Certificate to PEM file.
     */
    public void saveCertToPem(X509Certificate cert, File file) throws IOException {
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(new FileWriter(file))) {
            pemWriter.writeObject(cert);
        }
    }

    /**
     * Save Private Key to PEM file.
     */
    public void saveKeyToPem(PrivateKey key, File file) throws IOException {
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(new FileWriter(file))) {
            pemWriter.writeObject(key);
        }
    }

    /**
     * Save to JKS Keystore.
     */
    public void saveToKeystore(X509Certificate cert, PrivateKey key, String alias, String password, File file, String type) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(type); // JKS or PKCS12
        keyStore.load(null, null);
        
        Certificate[] chain = new Certificate[]{cert};
        keyStore.setKeyEntry(alias, key, password.toCharArray(), chain);

        try (FileOutputStream fos = new FileOutputStream(file)) {
            keyStore.store(fos, password.toCharArray());
        }
    }

    /**
     * Verify a certificate.
     */
    public void verifyCertificate(X509Certificate cert, X509Certificate issuerCert) throws Exception {
        cert.checkValidity(); // Check dates
        if (issuerCert != null) {
            cert.verify(issuerCert.getPublicKey()); // Check signature with issuer's public key
        } else {
             // Self-signed check
             cert.verify(cert.getPublicKey());
        }
    }
    
    /**
     * Load Certificate from file.
     */
    public X509Certificate loadCertificate(File file) throws Exception {
        try (FileInputStream fis = new FileInputStream(file)) {
            CertificateFactory fact = CertificateFactory.getInstance("X.509", BC_PROVIDER);
            return (X509Certificate) fact.generateCertificate(fis);
        }
    }
    /**
     * Load KeyStore from file.
     */
    public KeyStore loadKeystore(File file, String password, String type) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(type);
        try (FileInputStream fis = new FileInputStream(file)) {
            keyStore.load(fis, password == null ? null : password.toCharArray());
        }
        return keyStore;
    }
}
