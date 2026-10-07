package com.subshift.backend.gmail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/** AES-256-GCM. Stored format: base64( 12-byte IV || ciphertext+tag ). A fresh IV is used per encryption. */
@Component
public class TokenEncryptor {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public TokenEncryptor(@Value("${gmail.token-encryption-key}") String base64Key) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("TOKEN_ENCRYPTION_KEY must be base64-encoded", e);
        }
        if (raw.length != 32) {
            throw new IllegalStateException("TOKEN_ENCRYPTION_KEY must decode to exactly 32 bytes (AES-256)");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv).put(ciphertext).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Token encryption failed", e);
        }
    }

    public String decrypt(String stored) {
        try {
            byte[] all = Base64.getDecoder().decode(stored);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_LENGTH));
            return new String(cipher.doFinal(all, IV_LENGTH, all.length - IV_LENGTH), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Token decryption failed (wrong key or corrupted value)", e);
        }
    }
}
