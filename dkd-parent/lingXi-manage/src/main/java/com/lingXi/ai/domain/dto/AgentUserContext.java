package com.lingXi.ai.domain.dto;

import com.lingXi.common.core.domain.entity.SysRole;
import com.lingXi.common.core.domain.entity.SysUser;
import com.lingXi.common.core.domain.model.LoginUser;
import com.lingXi.manage.domain.Emp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * 由 Java 登录态生成并发送给 Python Agent 的可信用户上下文。
 * <p>该对象只包含允许进入模型运行时的白名单字段，不接受浏览器直接构造。</p>
 */
public final class AgentUserContext {

    private final String userId;
    private final String userName;
    private final String roleCode;
    private final String roleName;
    private final Long regionId;
    private final String regionName;
    private final List<String> permissions;

    public AgentUserContext(
            String userId,
            String userName,
            String roleCode,
            String roleName,
            Long regionId,
            String regionName,
            Collection<String> permissions) {
        if (isBlank(userId)) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        this.userId = userId.trim();
        this.userName = firstNonBlank(userName, this.userId);
        this.roleCode = trimToNull(roleCode);
        this.roleName = trimToNull(roleName);
        this.regionId = regionId;
        this.regionName = trimToNull(regionName);
        this.permissions = immutablePermissions(permissions);
    }

    /** 为仍使用旧服务签名的内部调用方创建最小兼容上下文。 */
    public static AgentUserContext minimal(String userId, String userName) {
        return new AgentUserContext(
                userId, userName, null, null, null, null, Collections.emptyList());
    }

    /**
     * 从已认证登录用户和可选员工业务档案创建白名单上下文。
     * 员工档案提供业务角色与区域；最终权限始终来自当前登录态。
     */
    public static AgentUserContext fromAuthenticated(LoginUser loginUser, Emp employee) {
        if (loginUser == null || loginUser.getUserId() == null) {
            throw new IllegalArgumentException("authenticated login user is required");
        }

        SysUser systemUser = loginUser.getUser();
        SysRole primaryRole = primaryRole(systemUser);
        String roleCode = firstNonBlank(
                employee == null ? null : employee.getRoleCode(),
                primaryRole == null ? null : primaryRole.getRoleKey());
        String roleName = firstNonBlank(
                employee == null ? null : employee.getRoleName(),
                primaryRole == null ? null : primaryRole.getRoleName());
        String userName = firstNonBlank(
                systemUser == null ? null : systemUser.getUserName(),
                systemUser == null ? null : systemUser.getNickName(),
                String.valueOf(loginUser.getUserId()));

        return new AgentUserContext(
                String.valueOf(loginUser.getUserId()),
                userName,
                roleCode,
                roleName,
                employee == null ? null : employee.getRegionId(),
                employee == null ? null : employee.getRegionName(),
                loginUser.getPermissions());
    }

    private static SysRole primaryRole(SysUser user) {
        if (user == null || user.getRoles() == null) {
            return null;
        }
        SysRole selected = null;
        for (SysRole role : user.getRoles()) {
            if (role == null) {
                continue;
            }
            if (selected == null || roleOrder(role) < roleOrder(selected)) {
                selected = role;
            }
        }
        return selected;
    }

    private static int roleOrder(SysRole role) {
        return role.getRoleSort() == null ? Integer.MAX_VALUE : role.getRoleSort();
    }

    private static List<String> immutablePermissions(Collection<String> values) {
        TreeSet<String> normalized = new TreeSet<>();
        if (values != null) {
            for (String value : values) {
                String permission = trimToNull(value);
                if (permission != null) {
                    normalized.add(permission);
                }
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(normalized));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return "";
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public String getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public String getRoleName() {
        return roleName;
    }

    public Long getRegionId() {
        return regionId;
    }

    public String getRegionName() {
        return regionName;
    }

    public List<String> getPermissions() {
        return permissions;
    }
}
