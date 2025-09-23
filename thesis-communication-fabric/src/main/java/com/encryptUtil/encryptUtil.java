package com.encryptUtil;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

public class encryptUtil {
    private static final String ALGO = "AES/CBC/PKCS5Padding";

    private static byte[] generateIV() {
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    public static String encrypt(String json, String secret) throws Exception {
        SecretKey key = new SecretKeySpec(secret.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance(ALGO);
        byte[] iv = generateIV();
        cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));

        byte[] encrypted = cipher.doFinal(json.getBytes());
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

        return Base64.getEncoder().encodeToString(combined);
    }

    public static String decrypt(String encryptedBase64, String secret) throws Exception {
        byte[] combined = Base64.getDecoder().decode(encryptedBase64);
        byte[] iv = new byte[16];
        byte[] ciphertext = new byte[combined.length - 16];
        System.arraycopy(combined, 0, iv, 0, 16);
        System.arraycopy(combined, 16, ciphertext, 0, ciphertext.length);

        SecretKey key = new SecretKeySpec(secret.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance(ALGO);
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));

        byte[] decrypted = cipher.doFinal(ciphertext);
        return new String(decrypted);
    }
}
