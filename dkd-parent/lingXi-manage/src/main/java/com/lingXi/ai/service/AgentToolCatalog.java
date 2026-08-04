package com.lingXi.ai.service;

import com.lingXi.ai.domain.dto.tool.AgentToolException;
import org.springframework.util.PatternMatchUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Java Tool Gateway 唯一的工具与权限白名单。 */
public final class AgentToolCatalog {

    public static final String QUERY_SALES_SUMMARY = "query_sales_summary";
    public static final String QUERY_TASK_STATISTICS = "query_task_statistics";
    public static final String QUERY_ABNORMAL_DEVICES = "query_abnormal_devices";
    public static final String LOOKUP_DEVICE = "lookup_device";
    public static final String GENERATE_IMAGE = "generate_image";
    public static final String PROPOSE_MAINTENANCE_TASK = "propose_maintenance_task";
    public static final String EXECUTE_MAINTENANCE_TASK = "execute_maintenance_task";

    private static final Map<String, String> REQUIRED_PERMISSIONS;

    static {
        Map<String, String> permissions = new LinkedHashMap<>();
        permissions.put(QUERY_SALES_SUMMARY, "manage:order:list");
        permissions.put(QUERY_TASK_STATISTICS, "manage:task:list");
        permissions.put(QUERY_ABNORMAL_DEVICES, "manage:vm:list");
        permissions.put(LOOKUP_DEVICE, "manage:vm:list");
        // 图片生成只要求已登录和持有本轮工具令牌，不绑定具体业务菜单权限。
        permissions.put(GENERATE_IMAGE, null);
        permissions.put(PROPOSE_MAINTENANCE_TASK, "manage:task:add");
        permissions.put(EXECUTE_MAINTENANCE_TASK, "manage:task:add");
        REQUIRED_PERMISSIONS = Collections.unmodifiableMap(permissions);
    }

    private AgentToolCatalog() {
    }

    public static String requiredPermission(String tool) {
        if (!REQUIRED_PERMISSIONS.containsKey(tool)) {
            throw new AgentToolException(
                    "TOOL_NOT_FOUND", "请求的工具不存在", 404, false);
        }
        return REQUIRED_PERMISSIONS.get(tool);
    }

    public static Set<String> allowedTools(Set<String> permissions) {
        Set<String> allowed = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : REQUIRED_PERMISSIONS.entrySet()) {
            if (entry.getValue() == null || hasPermission(permissions, entry.getValue())) {
                allowed.add(entry.getKey());
            }
        }
        return allowed;
    }

    private static boolean hasPermission(Set<String> permissions, String required) {
        if (permissions == null) {
            return false;
        }
        for (String permission : permissions) {
            if ("*:*:*".equals(permission)
                    || PatternMatchUtils.simpleMatch(permission, required)) {
                return true;
            }
        }
        return false;
    }
}
