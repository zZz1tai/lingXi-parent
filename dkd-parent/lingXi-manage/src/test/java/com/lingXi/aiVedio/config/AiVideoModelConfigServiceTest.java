package com.lingXi.aiVedio.config;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingXi.aiVedio.domain.dto.AiVideoModelConfig;
import com.lingXi.common.exception.ServiceException;
import com.lingXi.system.domain.SysConfig;
import com.lingXi.system.service.ISysConfigService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiVideoModelConfigServiceTest
{
    private final Map<String, String> values = new HashMap<>();
    private AiVideoModelConfigService service;

    @BeforeEach
    void setUp()
    {
        ISysConfigService sysConfigService = mock(ISysConfigService.class);
        AiVideoProviderProperties providerProperties = mock(AiVideoProviderProperties.class);
        when(providerProperties.getProvider()).thenReturn("happyhorse");
        when(sysConfigService.selectConfigByKey(anyString()))
                .thenAnswer(invocation -> values.get(invocation.getArgument(0)));
        when(sysConfigService.selectConfigList(any(SysConfig.class)))
                .thenAnswer(invocation -> matching(values, invocation.getArgument(0)));
        when(sysConfigService.insertConfig(any(SysConfig.class))).thenAnswer(invocation -> {
            SysConfig config = invocation.getArgument(0);
            values.put(config.getConfigKey(), config.getConfigValue());
            return 1;
        });
        when(sysConfigService.updateConfig(any(SysConfig.class))).thenAnswer(invocation -> {
            SysConfig config = invocation.getArgument(0);
            values.put(config.getConfigKey(), config.getConfigValue());
            return 1;
        });

        String masterKey = Base64.getEncoder().encodeToString(new byte[32]);
        service = new AiVideoModelConfigService();
        ReflectionTestUtils.setField(service, "sysConfigService", sysConfigService);
        ReflectionTestUtils.setField(service, "secretCipher", new AiVideoSecretCipher(masterKey));
        ReflectionTestUtils.setField(service, "videoProviderProperties", providerProperties);
    }

    @Test
    void databaseIsTheOnlySourceAndIncompleteConfigCannotRunTasks()
    {
        AiVideoModelConfig display = service.getConfig();

        assertNull(display.getTextModel());
        assertFalse(Boolean.TRUE.equals(display.getApiKeyConfigured()));
        assertThrows(ServiceException.class, service::getRequiredConfig);
    }

    @Test
    void savesEncryptedKeyReturnsOnlyMaskAndPreservesKeyWhenUnchanged() throws Exception
    {
        String apiKey = "sk-page-secret-1234567890";
        AiVideoModelConfig input = validInput();
        input.setApiKey(apiKey);

        AiVideoModelConfig display = service.updateConfig(input, "tester");

        String stored = values.get("aivideo.model.apiKey");
        assertTrue(stored.startsWith("enc:v1:"));
        assertFalse(stored.contains(apiKey));
        assertTrue(Boolean.TRUE.equals(display.getApiKeyConfigured()));
        assertEquals("sk-p********7890", display.getApiKeyMasked());
        assertNull(display.getApiKey());

        String json = new ObjectMapper().writeValueAsString(display);
        assertFalse(json.contains(apiKey));
        assertFalse(json.contains("\"apiKey\""));
        assertTrue(json.contains("apiKeyMasked"));

        AiVideoModelConfig runtime = service.getRequiredConfig();
        assertEquals(apiKey, runtime.getApiKey());
        assertEquals("deepseek-v4-flash", runtime.getTextModel());

        String originalCiphertext = stored;
        input.setApiKey(null);
        input.setTextModel("qwen-plus");
        service.updateConfig(input, "tester");
        assertEquals(originalCiphertext, values.get("aivideo.model.apiKey"));
        assertEquals(apiKey, service.getRequiredConfig().getApiKey());
        assertEquals("qwen-plus", service.getRequiredConfig().getTextModel());
    }

    private AiVideoModelConfig validInput()
    {
        AiVideoModelConfig input = new AiVideoModelConfig();
        input.setWorkspaceBaseUrl(
                "https://workspace.cn-beijing.maas.aliyuncs.com/compatible-mode/v1");
        input.setTextModel("deepseek-v4-flash");
        input.setImageModel("qwen-image-2.0-pro");
        input.setVideoProvider("happyhorse");
        input.setVideoModel("happyhorse-1.1-r2v");
        input.setVideoResolution("720P");
        input.setVideoRatio("16:9");
        input.setVideoWatermark(Boolean.FALSE);
        return input;
    }

    private List<SysConfig> matching(Map<String, String> stored, SysConfig query)
    {
        List<SysConfig> result = new ArrayList<>();
        String value = stored.get(query.getConfigKey());
        if (value != null)
        {
            SysConfig config = new SysConfig();
            config.setConfigId(1L);
            config.setConfigKey(query.getConfigKey());
            config.setConfigValue(value);
            result.add(config);
        }
        return result;
    }
}
