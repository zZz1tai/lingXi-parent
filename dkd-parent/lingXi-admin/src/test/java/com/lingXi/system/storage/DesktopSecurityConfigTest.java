package com.lingXi.system.storage;

import com.lingXi.system.config.SystemSecurityConfig;
import com.lingXi.system.config.SystemSecurityConfigService;
import com.lingXi.system.service.ISysConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DesktopSecurityConfigTest
{
    @Test
    void desktopCanSaveOptionalSearchSettingsWithoutAnyOssCredentials()
    {
        ISysConfigService configs = mock(ISysConfigService.class);
        when(configs.selectConfigByKey(anyString())).thenReturn("");
        when(configs.insertConfig(any())).thenReturn(1);
        SystemSecurityConfigService service = new SystemSecurityConfigService();
        ReflectionTestUtils.setField(service, "sysConfigService", configs);
        ReflectionTestUtils.setField(service, "desktop", true);
        assertDoesNotThrow(() -> service.updateConfig(new SystemSecurityConfig(), "admin"));
        verify(configs, never()).insertConfig(argThat(config -> config.getConfigKey().startsWith("security.oss.")));
    }
}
