package com.lingXi.system.service;

import com.lingXi.common.core.domain.entity.SysUser;
import com.lingXi.common.core.domain.model.LoginUser;
import com.lingXi.system.service.impl.SysUserOnlineServiceImpl;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SysUserOnlineSearchTest
{
    @Test
    void onlineSearchMatchesPartialAddressAndCaseInsensitiveUsername()
    {
        SysUser account = new SysUser();
        account.setUserName("BeijingAdmin");
        LoginUser user = new LoginUser();
        user.setUser(account);
        user.setIpaddr("192.168.12.34");
        SysUserOnlineServiceImpl service = new SysUserOnlineServiceImpl();
        assertNotNull(service.selectOnlineByIpaddr("168.12", user));
        assertNotNull(service.selectOnlineByUserName("ADMIN", user));
        assertNotNull(service.selectOnlineByInfo("12.34", "jing", user));
        assertNull(service.selectOnlineByInfo("12.35", "jing", user));
        assertNull(service.selectOnlineByInfo("12.34", "other", user));
        user.setIpaddr(null);
        assertNull(service.selectOnlineByIpaddr("168", user));
    }
}
