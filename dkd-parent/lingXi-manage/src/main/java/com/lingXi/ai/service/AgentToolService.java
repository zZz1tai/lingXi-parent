package com.lingXi.ai.service;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.lingXi.ai.domain.dto.tool.AbnormalDevicesArguments;
import com.lingXi.ai.domain.dto.tool.AgentToolException;
import com.lingXi.ai.domain.dto.tool.AgentToolGrant;
import com.lingXi.ai.domain.dto.tool.AgentToolRequest;
import com.lingXi.ai.domain.dto.tool.AgentToolResponse;
import com.lingXi.ai.domain.dto.tool.DeviceLookupArguments;
import com.lingXi.ai.domain.dto.tool.MaintenanceTaskExecuteArguments;
import com.lingXi.ai.domain.dto.tool.MaintenanceTaskProposalArguments;
import com.lingXi.ai.domain.dto.tool.SalesSummaryArguments;
import com.lingXi.ai.domain.dto.tool.TaskStatisticsArguments;
import com.lingXi.ai.mapper.AgentToolMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Tool Gateway 白名单、权限、区域、参数和数据最小化编排。 */
@Slf4j
@Service
public class AgentToolService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter SQL_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_DATE_RANGE_DAYS = 90;
    private static final int MAX_ABNORMAL_ROWS = 20;
    private static final Pattern INNER_CODE = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final AgentToolTokenService tokenService;
    private final AgentToolMapper toolMapper;
    private final AgentWriteActionService writeActionService;
    private final ObjectMapper strictMapper;

    public AgentToolService(
            AgentToolTokenService tokenService,
            AgentToolMapper toolMapper,
            AgentWriteActionService writeActionService,
            ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.toolMapper = toolMapper;
        this.writeActionService = writeActionService;
        this.strictMapper = objectMapper.rebuild()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                .configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true)
                .build();
    }

    public AgentToolResponse invoke(
            String token,
            String headerRequestId,
            JsonNode rawRequest) {
        long started = System.nanoTime();
        String tool = safeToolName(rawRequest);
        String requestId = safeValue(headerRequestId, 128);
        AgentToolGrant grant = null;
        int resultCount = 0;
        boolean succeeded = false;
        try {
            AgentToolRequest request = parseRequest(rawRequest);
            tool = requireLabel(request.getTool(), "tool", 64);
            AgentToolRequest.RequestContext requestContext = request.getRequestContext();
            if (requestContext == null) {
                throw invalid("request_context不能为空");
            }
            requestId = requireLabel(
                    requestContext.getAgentRequestId(), "agent_request_id", 128);
            String threadId = requireLabel(requestContext.getThreadId(), "thread_id", 128);
            if (!requestId.equals(safeValue(headerRequestId, 128))) {
                throw new AgentToolException(
                        "TOOL_UNAUTHORIZED", "工具请求标识不匹配", 401, false);
            }
            grant = tokenService.validateAndConsume(token, requestId, threadId, tool);
            String requiredPermission = AgentToolCatalog.requiredPermission(tool);
            if (!grant.hasPermission(requiredPermission)) {
                throw new AgentToolException(
                        "TOOL_UNAUTHORIZED", "当前用户无权使用该工具", 403, false);
            }
            InvocationResult result = execute(tool, request.getArguments(), grant);
            resultCount = result.resultCount;
            succeeded = true;
            long elapsedMs = elapsedMillis(started);
            return AgentToolResponse.success(
                    result.data,
                    metadata(requestId, tool, elapsedMs, result.truncated));
        } finally {
            log.info(
                    "Agent tool audit | caller={} | request={} | tool={} | regionId={} | "
                            + "success={} | resultCount={} | elapsedMs={}",
                    grant == null ? "unknown" : digestLabel(grant.getUserId()),
                    digestLabel(requestId),
                    safeValue(tool, 64),
                    grant == null ? null : grant.getRegionId(),
                    succeeded,
                    resultCount,
                    elapsedMillis(started));
        }
    }

    public AgentToolResponse.Metadata failureMetadata(
            String requestId, String tool, long elapsedMs) {
        return new AgentToolResponse.Metadata(
                safeValue(requestId, 128),
                safeValue(tool, 64),
                elapsedMs,
                generatedAt(),
                false,
                false);
    }

    private InvocationResult execute(
            String tool, JsonNode arguments, AgentToolGrant grant) {
        if (arguments == null || !arguments.isObject()) {
            throw invalid("arguments必须是JSON对象");
        }
        switch (tool) {
            case AgentToolCatalog.QUERY_SALES_SUMMARY:
                return querySales(arguments, grant);
            case AgentToolCatalog.QUERY_TASK_STATISTICS:
                return queryTasks(arguments, grant);
            case AgentToolCatalog.QUERY_ABNORMAL_DEVICES:
                return queryAbnormalDevices(arguments, grant);
            case AgentToolCatalog.LOOKUP_DEVICE:
                return lookupDevice(arguments, grant);
            case AgentToolCatalog.PROPOSE_MAINTENANCE_TASK:
                return new InvocationResult(
                        writeActionService.propose(
                                grant,
                                convert(arguments, MaintenanceTaskProposalArguments.class)),
                        false,
                        1);
            case AgentToolCatalog.EXECUTE_MAINTENANCE_TASK:
                return new InvocationResult(
                        writeActionService.execute(
                                grant,
                                convert(arguments, MaintenanceTaskExecuteArguments.class)),
                        false,
                        1);
            default:
                throw new AgentToolException(
                        "TOOL_NOT_FOUND", "请求的工具不存在", 404, false);
        }
    }

    private InvocationResult querySales(JsonNode arguments, AgentToolGrant grant) {
        SalesSummaryArguments args = convert(arguments, SalesSummaryArguments.class);
        DateRange range = requireDateRange(args.getStart(), args.getEnd());
        String granularity = safeValue(args.getGranularity(), 16).toLowerCase();
        if (!"day".equals(granularity) && !"month".equals(granularity)) {
            throw invalid("granularity仅支持day或month");
        }
        Long regionId = resolveRegion(grant, args.getRegionId());
        Map<String, Object> metrics = nullToEmpty(toolMapper.selectSalesSummary(
                range.startSql, range.endExclusiveSql, regionId));
        List<Map<String, Object>> trend = toolMapper.selectSalesTrend(
                range.startSql, range.endExclusiveSql, regionId, granularity);
        Map<String, Object> dimensions = new LinkedHashMap<>();
        dimensions.put("trend", trend == null ? Collections.emptyList() : trend);
        Map<String, Object> data = baseDataset(
                "sales_summary", grant, regionId, range, metrics, dimensions,
                Collections.emptyList(), "分", false);
        data.put("currency", "CNY");
        data.put("amount_unit", "cent");
        data.put("granularity", granularity);
        return new InvocationResult(data, false, trend == null ? 0 : trend.size());
    }

    private InvocationResult queryTasks(JsonNode arguments, AgentToolGrant grant) {
        TaskStatisticsArguments args = convert(arguments, TaskStatisticsArguments.class);
        DateRange range = requireDateRange(args.getStart(), args.getEnd());
        if (args.getTaskType() != null
                && (args.getTaskType().intValue() < 1 || args.getTaskType().intValue() > 4)) {
            throw invalid("task_type必须在1到4之间");
        }
        Long regionId = resolveRegion(grant, args.getRegionId());
        Map<String, Object> metrics = nullToEmpty(toolMapper.selectTaskStatistics(
                range.startSql, range.endExclusiveSql, regionId, args.getTaskType()));
        Map<String, Object> dimensions = new LinkedHashMap<>();
        dimensions.put("task_type", args.getTaskType());
        Map<String, Object> data = baseDataset(
                "task_statistics", grant, regionId, range, metrics, dimensions,
                Collections.emptyList(), "个", false);
        data.put("duration_unit", "minute");
        return new InvocationResult(data, false, metrics.isEmpty() ? 0 : 1);
    }

    private InvocationResult queryAbnormalDevices(
            JsonNode arguments, AgentToolGrant grant) {
        AbnormalDevicesArguments args = convert(arguments, AbnormalDevicesArguments.class);
        int limit = args.getLimit() == null ? 10 : args.getLimit().intValue();
        if (limit < 1 || limit > MAX_ABNORMAL_ROWS) {
            throw invalid("limit必须在1到20之间");
        }
        Long regionId = resolveRegion(grant, args.getRegionId());
        int total = toolMapper.countAbnormalDevices(regionId);
        List<Map<String, Object>> rows = toolMapper.selectAbnormalDevices(regionId, limit);
        if (rows == null) {
            rows = Collections.emptyList();
        }
        boolean truncated = total > rows.size();
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("current_abnormal_count", total);
        Map<String, Object> data = baseDataset(
                "current_abnormal_devices", grant, regionId, null, metrics,
                Collections.emptyMap(), rows, "台", truncated);
        data.put("statistics_basis", "当前设备运行状态快照，不代表历史故障事件数");
        return new InvocationResult(data, truncated, rows.size());
    }

    private InvocationResult lookupDevice(JsonNode arguments, AgentToolGrant grant) {
        DeviceLookupArguments args = convert(arguments, DeviceLookupArguments.class);
        String innerCode = safeValue(args.getInnerCode(), 64);
        if (!INNER_CODE.matcher(innerCode).matches()) {
            throw invalid("inner_code格式无效");
        }
        Long regionId = resolveRegion(grant, args.getRegionId());
        Map<String, Object> device = toolMapper.selectDeviceByInnerCodeAndRegion(
                innerCode, regionId);
        if (device == null || device.isEmpty()) {
            throw new AgentToolException(
                    "TOOL_NOT_FOUND", "权限范围内未找到该设备", 404, false);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(device);
        Map<String, Object> data = baseDataset(
                "device_status", grant, regionId, null, Collections.emptyMap(),
                Collections.emptyMap(), rows, "台", false);
        return new InvocationResult(data, false, 1);
    }

    private AgentToolRequest parseRequest(JsonNode rawRequest) {
        if (rawRequest == null || !rawRequest.isObject()) {
            throw invalid("请求体必须是JSON对象");
        }
        try {
            return strictMapper.treeToValue(rawRequest, AgentToolRequest.class);
        } catch (Exception exception) {
            throw invalid("工具请求结构无效");
        }
    }

    private <T> T convert(JsonNode arguments, Class<T> type) {
        try {
            return strictMapper.treeToValue(arguments, type);
        } catch (Exception exception) {
            throw invalid("工具参数结构无效");
        }
    }

    private static Long resolveRegion(AgentToolGrant grant, Long requestedRegionId) {
        if (requestedRegionId != null && requestedRegionId.longValue() <= 0L) {
            throw invalid("region_id必须为正整数");
        }
        if (grant.getRegionId() != null) {
            if (requestedRegionId != null
                    && !grant.getRegionId().equals(requestedRegionId)) {
                throw new AgentToolException(
                        "TOOL_SCOPE_EMPTY", "请求区域不在当前用户可见范围内", 403, false);
            }
            return grant.getRegionId();
        }
        if (grant.hasPermission("*:*:*") && requestedRegionId != null) {
            return requestedRegionId;
        }
        throw new AgentToolException(
                "TOOL_SCOPE_EMPTY", "当前用户没有可用于查询的区域范围", 403, false);
    }

    private static DateRange requireDateRange(String startValue, String endValue) {
        try {
            LocalDate start = LocalDate.parse(requireLabel(startValue, "start", 10));
            LocalDate end = LocalDate.parse(requireLabel(endValue, "end", 10));
            long days = ChronoUnit.DAYS.between(start, end);
            if (days < 0 || days >= MAX_DATE_RANGE_DAYS) {
                throw invalid("日期范围必须按顺序且不超过90天");
            }
            return new DateRange(
                    start,
                    end,
                    start.atStartOfDay().format(SQL_TIME),
                    end.plusDays(1).atStartOfDay().format(SQL_TIME));
        } catch (DateTimeParseException exception) {
            throw invalid("日期必须使用YYYY-MM-DD格式");
        }
    }

    private static Map<String, Object> baseDataset(
            String dataset,
            AgentToolGrant grant,
            Long regionId,
            DateRange range,
            Map<String, Object> metrics,
            Map<String, Object> dimensions,
            List<Map<String, Object>> rows,
            String unit,
            boolean truncated) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dataset", dataset);
        data.put("generated_at", generatedAt());
        data.put("timezone", BUSINESS_ZONE.getId());
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("region_ids", Collections.singletonList(regionId));
        if (grant.getRegionId() != null
                && grant.getRegionId().equals(regionId)
                && grant.getRegionName() != null
                && !grant.getRegionName().trim().isEmpty()) {
            scope.put("region_names", Collections.singletonList(grant.getRegionName()));
        } else {
            scope.put("region_names", Collections.emptyList());
        }
        scope.put("permission_filtered", true);
        data.put("scope", scope);
        if (range != null) {
            Map<String, Object> timeRange = new LinkedHashMap<>();
            timeRange.put("start", range.start.toString());
            timeRange.put("end", range.end.toString());
            data.put("time_range", timeRange);
        } else {
            data.put("time_range", null);
        }
        data.put("metrics", metrics);
        data.put("dimensions", dimensions);
        data.put("rows", rows);
        data.put("unit", unit);
        data.put("truncated", truncated);
        data.put("sampled", false);
        return data;
    }

    private static AgentToolResponse.Metadata metadata(
            String requestId, String tool, long elapsedMs, boolean truncated) {
        return new AgentToolResponse.Metadata(
                requestId, tool, elapsedMs, generatedAt(), true, truncated);
    }

    private static String generatedAt() {
        return OffsetDateTime.now(BUSINESS_ZONE).toString();
    }

    private static Map<String, Object> nullToEmpty(Map<String, Object> value) {
        return value == null ? Collections.emptyMap() : value;
    }

    private static AgentToolException invalid(String message) {
        return new AgentToolException(
                "TOOL_INVALID_ARGUMENT", message, 400, false);
    }

    private static String requireLabel(String value, String field, int maxLength) {
        String normalized = safeValue(value, maxLength);
        if (normalized.isEmpty()) {
            throw invalid(field + "不能为空");
        }
        return normalized;
    }

    private static String safeToolName(JsonNode rawRequest) {
        return rawRequest == null ? "" : safeValue(rawRequest.path("tool").asText(""), 64);
    }

    private static String safeValue(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength
                || normalized.indexOf('\r') >= 0
                || normalized.indexOf('\n') >= 0
                || normalized.indexOf('\0') >= 0) {
            return "";
        }
        return normalized;
    }

    private static long elapsedMillis(long started) {
        return Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
    }

    private static String digestLabel(String value) {
        if (value == null || value.isEmpty()) {
            return "unknown";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(12);
            for (int index = 0; index < 6; index++) {
                result.append(String.format("%02x", digest[index]));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            return "unavailable";
        }
    }

    private static final class DateRange {
        private final LocalDate start;
        private final LocalDate end;
        private final String startSql;
        private final String endExclusiveSql;

        private DateRange(
                LocalDate start, LocalDate end, String startSql, String endExclusiveSql) {
            this.start = start;
            this.end = end;
            this.startSql = startSql;
            this.endExclusiveSql = endExclusiveSql;
        }
    }

    private static final class InvocationResult {
        private final Map<String, Object> data;
        private final boolean truncated;
        private final int resultCount;

        private InvocationResult(
                Map<String, Object> data, boolean truncated, int resultCount) {
            this.data = data;
            this.truncated = truncated;
            this.resultCount = resultCount;
        }
    }
}
