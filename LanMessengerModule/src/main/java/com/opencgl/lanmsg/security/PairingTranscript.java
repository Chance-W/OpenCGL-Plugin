package com.opencgl.lanmsg.security;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.StringJoiner;

/** Full verification value. Inputs must come from the current TLS session, never discovery/display fields. */
public final class PairingTranscript {
    private PairingTranscript() { }

    public static String verificationCode(X509Certificate initiator, byte[] initiatorNonce,
                                          X509Certificate responder, byte[] responderNonce)
            throws GeneralSecurityException {
        if (initiatorNonce == null || initiatorNonce.length != 32 || responderNonce == null || responderNonce.length != 32)
            throw new IllegalArgumentException("Pairing requires two 32-byte fresh nonces");
        String initiatorId = DeviceIdentity.validateCertificate(initiator);
        String responderId = DeviceIdentity.validateCertificate(responder);
        byte[] initiatorKey = initiator.getPublicKey().getEncoded(), responderKey = responder.getPublicKey().getEncoded();
        if (initiatorId.equals(responderId) || MessageDigest.isEqual(initiatorKey, responderKey))
            throw new CertificateException("Cannot pair a device with its own identity");
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                field(out, "OpenCGL-LAN/TLS1.3/pairing/v1".getBytes(StandardCharsets.US_ASCII));
                field(out, "initiator".getBytes(StandardCharsets.US_ASCII));
                field(out, initiatorId.getBytes(StandardCharsets.US_ASCII));
                field(out, initiatorKey); field(out, initiatorNonce);
                field(out, "responder".getBytes(StandardCharsets.US_ASCII));
                field(out, responderId.getBytes(StandardCharsets.US_ASCII));
                field(out, responderKey); field(out, responderNonce);
            }
            String hex = HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
            var groups = new StringJoiner(" ");
            for (int i = 0; i < hex.length(); i += 8) groups.add(hex.substring(i, i + 8));
            return groups.toString();
        } catch (IOException impossible) { throw new IllegalStateException("Memory transcript encoding failed", impossible); }
    }

    private static void field(DataOutputStream output, byte[] bytes) throws IOException {
        output.writeInt(bytes.length); output.write(bytes);
    }
}
