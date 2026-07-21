package com.lingXi.aiVedio.config;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import com.lingXi.common.exception.ServiceException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiVideoSecretCipherTest
{
    private static final String MASTER_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encryptsWithRandomIvAndDecryptsWithoutLeakingPlaintext()
    {
        AiVideoSecretCipher cipher = new AiVideoSecretCipher(MASTER_KEY);
        String apiKey = "sk-test-secret-1234567890";

        String first = cipher.encrypt(apiKey);
        String second = cipher.encrypt(apiKey);

        assertTrue(first.startsWith("enc:v1:"));
        assertFalse(first.contains(apiKey));
        assertNotEquals(first, second);
        assertEquals(apiKey, cipher.decrypt(first));
        assertEquals(apiKey, cipher.decrypt(second));
    }

    @Test
    void rejectsMissingOrInvalidMasterKeys()
    {
        ServiceException missing = assertThrows(ServiceException.class,
                () -> new AiVideoSecretCipher("").encrypt("sk-test-secret"));
        assertTrue(missing.getMessage().contains("AIVIDEO_CONFIG_ENCRYPTION_KEY"));

        ServiceException invalid = assertThrows(ServiceException.class,
                () -> new AiVideoSecretCipher("not-base64").encrypt("sk-test-secret"));
        assertTrue(invalid.getMessage().contains("Base64"));
    }

    @Test
    void refusesUnencryptedLegacyValues()
    {
        AiVideoSecretCipher cipher = new AiVideoSecretCipher(MASTER_KEY);
        assertThrows(ServiceException.class, () -> cipher.decrypt("sk-legacy-plaintext"));
    }
}
