package com.opencgl.solace.persistence;

public interface SecretProtector {
    String protect(String plainText);
    String unprotect(String protectedText);
}
