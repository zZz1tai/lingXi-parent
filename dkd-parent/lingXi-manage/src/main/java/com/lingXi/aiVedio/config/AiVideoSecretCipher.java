package com.lingXi.aiVedio.config;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.lingXi.common.exception.ServiceException;

/** 使用部署环境中的独立主密钥加密页面保存的 AI 服务凭据。 */
@Component
public class AiVideoSecretCipher
{
    private static final String PREFIX = "enc:v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String encodedMasterKey;

    public AiVideoSecretCipher(
            @Value("${aivideo.secret.encryption-key:}") String encodedMasterKey)
    {
        this.encodedMasterKey = encodedMasterKey == null ? "" : encodedMasterKey.trim();
    }

    public String encrypt(String plaintext)
    {
        if (plaintext == null || plaintext.isEmpty())
        {
            throw new ServiceException("API Key 不能为空");
        }
        try
        {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        }
        catch (ServiceException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw new ServiceException("API Key 加密失败");
        }
    }

    public String decrypt(String storedValue)
    {
        if (storedValue == null || !storedValue.startsWith(PREFIX))
        {
            throw new ServiceException("已保存的 API Key 不是受支持的加密格式，请在模型配置页面重新填写");
        }
        try
        {
            byte[] payload = Base64.getDecoder().decode(storedValue.substring(PREFIX.length()));
            if (payload.length <= IV_BYTES)
            {
                throw new IllegalArgumentException("encrypted payload is too short");
            }
            byte[] iv = new byte[IV_BYTES];
            byte[] encrypted = new byte[payload.length - IV_BYTES];
            System.arraycopy(payload, 0, iv, 0, iv.length);
            System.arraycopy(payload, iv.length, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, masterKey(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        }
        catch (ServiceException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw new ServiceException("API Key 解密失败，请检查 AIVIDEO_CONFIG_ENCRYPTION_KEY 后重新保存");
        }
    }

    private SecretKeySpec masterKey()
    {
        if (encodedMasterKey.isEmpty())
        {
            throw new ServiceException("未配置 AIVIDEO_CONFIG_ENCRYPTION_KEY，无法安全保存或读取 API Key");
        }
        try
        {
            byte[] key = Base64.getDecoder().decode(encodedMasterKey);
            if (key.length != KEY_BYTES)
            {
                throw new IllegalArgumentException("master key must contain 32 bytes");
            }
            return new SecretKeySpec(key, "AES");
        }
        catch (IllegalArgumentException ex)
        {
            throw new ServiceException("AIVIDEO_CONFIG_ENCRYPTION_KEY 必须是 Base64 编码的 32 字节密钥");
        }
    }
}
