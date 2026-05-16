package com.cloudalbum.publisher.common.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Slf4j
@Service
public class CredentialCryptoService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;
    private static final String DEFAULT_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Value("${media-source.crypto.secret:" + DEFAULT_SECRET + "}")
    private String secret;

    private final SecureRandom secureRandom = new SecureRandom();

    private SecretKeySpec secretKeySpec;

    @PostConstruct
    public void init() {
        if (DEFAULT_SECRET.equals(secret)) {
            log.warn("Using default encryption secret for media source credentials! "
                    + "Configure 'media-source.crypto.secret' with a unique key for production use.");
        }

        byte[] key = Base64.getDecoder().decode(secret);
        if (key.length != 16 && key.length != 24 && key.length != 32) {
            throw new IllegalStateException("media-source.crypto.secret 必须是 16/24/32 字节 AES key 的 Base64 值");
        }
        this.secretKeySpec = new SecretKeySpec(key, "AES");
    }

    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTE];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_LENGTH_BIT, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("加密凭证失败", ex);
        }
    }

    public String decrypt(String cipherText) {
        if (!StringUtils.hasText(cipherText)) {
            return null;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(cipherText);
            if (payload.length <= IV_LENGTH_BYTE) {
                throw new IllegalStateException("凭证密文格式无效");
            }
            byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH_BYTE);
            byte[] encrypted = Arrays.copyOfRange(payload, IV_LENGTH_BYTE, payload.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_LENGTH_BIT, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("解密凭证失败", ex);
        }
    }
}
